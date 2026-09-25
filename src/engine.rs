use std::collections::HashSet;
use std::pin::Pin;
use std::rc::Rc;

use cxx::UniquePtr;

use crate::cache::Cache;
use crate::error::{Error, Result};
use crate::key::{self, Key};
use crate::model::{
    Curve2, Curve3, FilletKind, Model, Node, Path, Point2, Point3, Profile, RadiusSpec, SweepMode,
    TransformOp,
};
use crate::sys::ffi;

/// A built shape (a compound of solids). Cheap to clone — operands and cache hits share it.
#[derive(Clone)]
pub struct Part(Rc<PartShape>);

struct PartShape {
    shape: UniquePtr<ffi::Shape>,
}

impl Part {
    /// Wraps an already-built shape (used by the cache to revive a stored part).
    pub(crate) fn from_shape(shape: UniquePtr<ffi::Shape>) -> Part {
        Part(Rc::new(PartShape { shape }))
    }

    pub(crate) fn shape(&self) -> &ffi::Shape {
        self.0.shape.as_ref().expect("shape handle is never null")
    }

    pub fn solid_count(&self) -> usize {
        ffi::solid_count(self.shape())
    }

    pub fn face_count(&self) -> usize {
        ffi::face_count(self.shape())
    }

    pub fn volume(&self) -> f64 {
        ffi::volume(self.shape())
    }

    pub fn bounding_box(&self) -> [f64; 6] {
        let values = ffi::bounding_box(self.shape());
        let mut bounds = [0.0; 6];
        bounds.copy_from_slice(&values[..6]);
        bounds
    }
}

fn make_part(shape: UniquePtr<ffi::Shape>) -> Result<Part> {
    if !ffi::is_solids_only(&shape) {
        return Err(Error::NotASolid);
    }
    let unified = ffi::unify(&shape)?;
    if !ffi::is_solids_only(&unified) {
        return Err(Error::NotASolid);
    }
    Ok(Part(Rc::new(PartShape { shape: unified })))
}

/// Evaluates the model's roots. The cache is not passed in directly: the engine owns the
/// `Part` <-> bytes codec (its `BrepCodec`), hands it to `make_cache`, and uses whatever cache the
/// factory builds. This keeps the cache fully unaware of the domain model.
pub fn evaluate<C, F>(model: &Model, make_cache: F) -> Result<Vec<Part>>
where
    C: Cache<Key, Part>,
    F: FnOnce(BrepCodec) -> C,
{
    // One `try_map` pass over the arena: validate every reference and collect reachability.
    let mut referenced = HashSet::new();
    for (index, node) in model.parts.iter().enumerate() {
        node.try_map(|operand| {
            validate_index(operand, index)?;
            referenced.insert(operand);
            Ok(())
        })?;
    }

    // Roots are the nodes nobody references.
    let mut cache = make_cache(BrepCodec);
    let parts = &model.parts;
    let mut roots = Vec::new();
    for (index, node) in parts.iter().enumerate() {
        if !referenced.contains(&index) {
            roots.push(get_or_evaluate(node, parts, &mut cache)?);
        }
    }
    Ok(roots)
}

/// `Part` <-> bytes, as OpenCASCADE BREP — the engine's own codec for the disk cache.
pub struct BrepCodec;

impl crate::cache::Codec<Part> for BrepCodec {
    fn encode(&self, part: &Part) -> Result<Vec<u8>> {
        Ok(ffi::brep_encode(part.shape())?)
    }

    fn decode(&self, bytes: &[u8]) -> Result<Part> {
        Ok(Part::from_shape(ffi::brep_decode(bytes)?))
    }
}

/// The whole `Node -> Part` transformation. The cache key is a purely internal detail: computed
/// here, right before use, and never leaving this function. The cache is whatever the caller
/// passed in — memory, disk, a layering of both, or nothing.
fn get_or_evaluate<C: Cache<Key, Part>>(
    node: &Node<usize>,
    parts: &[Node<usize>],
    cache: &mut C,
) -> Result<Part> {
    let key = key_of(node, parts)?;
    cache.get_or_put(key, |cache| {
        let ready: Node<Part> =
            node.try_map(|operand| get_or_evaluate(&parts[operand], parts, cache))?;
        ready.evaluate()
    })
}

/// The node's Merkle key `H(node ‖ operand_keys…)`, computed on demand (keys are not stored).
/// References are `index < current` (checked in [`evaluate`]), so `parts[operand]` is in range.
fn key_of(node: &Node<usize>, parts: &[Node<usize>]) -> Result<Key> {
    let mapped: Node<Key> = node.try_map(|operand| key_of(&parts[operand], parts))?;
    Ok(key::fingerprint(&mapped))
}

fn validate_index(index: usize, current: usize) -> Result<()> {
    if index >= current {
        Err(Error::InvalidReference { index, current })
    } else {
        Ok(())
    }
}

impl Node<Part> {
    /// Applies the node's operation; all operands are already evaluated (see [`build`]).
    fn evaluate(self) -> Result<Part> {
        match self {
            Node::Box {
                width,
                length,
                height,
            } => make_part(ffi::make_box(
                width.value(),
                length.value(),
                height.value(),
            )?),
            Node::Sphere { radius } => make_part(ffi::make_sphere(radius.value())?),
            Node::Cylinder { radius, height } => {
                make_part(ffi::make_cylinder(radius.value(), height.value())?)
            }
            Node::Cone {
                radius_bottom,
                radius_top,
                height,
            } => make_part(ffi::make_cone(
                radius_bottom.value(),
                radius_top.value(),
                height.value(),
            )?),
            Node::Torus {
                major_radius,
                minor_radius,
            } => make_part(ffi::make_torus(major_radius.value(), minor_radius.value())?),
            Node::Wedge {
                width,
                length,
                height,
                top_width,
            } => make_part(ffi::make_wedge(
                width.value(),
                length.value(),
                height.value(),
                top_width.value(),
            )?),
            Node::Halfspace => make_part(ffi::make_halfspace()?),
            Node::Fuse { parts } => reduce(parts.into_iter(), |a, b| Ok(ffi::fuse(a, b)?)),
            Node::Cut { base, tools } => reduce(std::iter::once(base).chain(tools), |a, b| {
                Ok(ffi::cut(a, b)?)
            }),
            Node::Common { parts } => reduce(parts.into_iter(), |a, b| Ok(ffi::common(a, b)?)),
            Node::Transform { target, ops } => {
                let mut result = target;
                for op in &ops {
                    result = apply_transform(result, op)?;
                }
                Ok(result)
            }
            Node::Extrude { profile, height } => {
                let wire = build_profile_wire(&profile)?;
                make_part(ffi::extrude(&wire, height.value())?)
            }
            Node::Revolve { profile, angle } => {
                let wire = build_profile_wire(&profile)?;
                make_part(ffi::revolve(&wire, angle.value())?)
            }
            Node::Sweep {
                profile,
                path,
                mode,
            } => {
                let profile_wire = build_profile_wire(&profile)?;
                let spine = build_path_wire(&path, false)?;
                let follow = matches!(mode, SweepMode::Follow);
                make_part(ffi::sweep(&profile_wire, &spine, follow)?)
            }
            Node::Loft { sections, ruled } => {
                if sections.len() < 2 {
                    return Err(Error::LoftNeedsTwoSections);
                }
                let mut builder = ffi::new_loft_builder(ruled);
                for section in &sections {
                    let wire = build_path_wire(section, true)?;
                    builder.pin_mut().add(&wire)?;
                }
                make_part(builder.pin_mut().finish()?)
            }
            Node::Fillet {
                target,
                kind,
                radius,
            } => evaluate_fillet(target, kind, &radius),
        }
    }
}

/// Reduces already-evaluated operands with a binary operation (e.g. `fuse`/`cut`/`common`).
fn reduce<F>(operands: impl Iterator<Item = Part>, combine: F) -> Result<Part>
where
    F: Fn(&ffi::Shape, &ffi::Shape) -> Result<UniquePtr<ffi::Shape>>,
{
    let mut operands = operands;
    let first = operands.next().ok_or(Error::MissingOperand)?;
    operands.try_fold(first, |accumulator, next| {
        make_part(combine(accumulator.shape(), next.shape())?)
    })
}

fn apply_transform(part: Part, op: &TransformOp) -> Result<Part> {
    match op {
        TransformOp::Translate { value } => make_part(ffi::translate(
            part.shape(),
            value.dx.value(),
            value.dy.value(),
            value.dz.value(),
        )?),
        TransformOp::Rotate {
            center,
            axis,
            angle,
        } => make_part(ffi::rotate(
            part.shape(),
            center.x.value(),
            center.y.value(),
            center.z.value(),
            axis.dx.value(),
            axis.dy.value(),
            axis.dz.value(),
            angle.value(),
        )?),
        TransformOp::Mirror { center, normal } => make_part(ffi::mirror(
            part.shape(),
            center.x.value(),
            center.y.value(),
            center.z.value(),
            normal.dx.value(),
            normal.dy.value(),
            normal.dz.value(),
        )?),
        TransformOp::Scale { x, y, z } => {
            make_part(ffi::scale(part.shape(), x.value(), y.value(), z.value())?)
        }
        TransformOp::Matrix { m } => {
            let values: Vec<f64> = m.iter().map(|scalar| scalar.value()).collect();
            make_part(ffi::apply_matrix(part.shape(), &values)?)
        }
    }
}

fn evaluate_fillet(target: Part, kind: FilletKind, radius: &RadiusSpec) -> Result<Part> {
    let shape = target.shape();
    let engine = expression_engine();
    let mut values = Vec::new();
    for solid in 0..ffi::solid_count(shape) {
        for edge in 0..ffi::solid_edge_count(shape, solid) {
            let data = ffi::solid_edge_data(shape, solid, edge);
            let value = match radius {
                RadiusSpec::All { radius } => radius.value(),
                RadiusSpec::Expression { expression } => {
                    evaluate_expression(&engine, expression, &data)?
                }
            };
            values.push(value);
        }
    }
    let kind_code = match kind {
        FilletKind::Fillet => 0,
        FilletKind::Chamfer => 1,
    };
    make_part(ffi::fillet(shape, kind_code, &values)?)
}

fn expression_engine() -> rhai::Engine {
    let mut engine = rhai::Engine::new();
    engine.set_max_operations(10_000);
    engine
}

fn evaluate_expression(engine: &rhai::Engine, expression: &str, data: &[f64]) -> Result<f64> {
    let mut scope = rhai::Scope::new();
    scope.push_constant("edge", build_edge(data));
    let result = engine
        .eval_expression_with_scope::<rhai::Dynamic>(&mut scope, expression)
        .map_err(|error| Error::Expression(format!("{expression}: {error}")))?;
    let value = if let Some(number) = result.clone().try_cast::<f64>() {
        number
    } else if let Some(number) = result.clone().try_cast::<i64>() {
        number as f64
    } else {
        return Err(Error::Expression(format!(
            "{expression}: result is not a number"
        )));
    };
    if value.is_nan() {
        return Err(Error::Expression(format!("{expression}: result is NaN")));
    }
    Ok(value)
}

fn build_edge(data: &[f64]) -> rhai::Map {
    let mut edge = rhai::Map::new();
    edge.insert("length".into(), rhai::Dynamic::from(data[0]));
    edge.insert(
        "curve_type".into(),
        rhai::Dynamic::from(curve_type_name(data[1]).to_string()),
    );
    edge.insert("is_vertical".into(), rhai::Dynamic::from(data[2] != 0.0));
    edge.insert("is_horizontal".into(), rhai::Dynamic::from(data[3] != 0.0));

    let mut direction = rhai::Map::new();
    direction.insert("dx".into(), rhai::Dynamic::from(data[4]));
    direction.insert("dy".into(), rhai::Dynamic::from(data[5]));
    direction.insert("dz".into(), rhai::Dynamic::from(data[6]));
    edge.insert("direction".into(), rhai::Dynamic::from(direction));

    edge.insert("radius".into(), rhai::Dynamic::from(data[7]));

    let mut start = rhai::Map::new();
    start.insert("x".into(), rhai::Dynamic::from(data[8]));
    start.insert("y".into(), rhai::Dynamic::from(data[9]));
    start.insert("z".into(), rhai::Dynamic::from(data[10]));
    edge.insert("start".into(), rhai::Dynamic::from(start));

    let mut end = rhai::Map::new();
    end.insert("x".into(), rhai::Dynamic::from(data[11]));
    end.insert("y".into(), rhai::Dynamic::from(data[12]));
    end.insert("z".into(), rhai::Dynamic::from(data[13]));
    edge.insert("end".into(), rhai::Dynamic::from(end));

    edge
}

fn curve_type_name(code: f64) -> &'static str {
    match code as i32 {
        0 => "line",
        1 => "arc",
        _ => "spline",
    }
}

fn build_profile_wire(profile: &Profile) -> Result<UniquePtr<ffi::Shape>> {
    if profile.edges.is_empty() {
        return Err(Error::EmptyContour);
    }
    let mut builder = ffi::new_wire_builder();
    builder
        .pin_mut()
        .start(profile.start.x.value(), profile.start.y.value(), 0.0)?;
    for edge in &profile.edges {
        add_curve2(builder.pin_mut(), edge)?;
    }
    Ok(builder.pin_mut().finish(true)?)
}

fn build_path_wire(path: &Path, closed: bool) -> Result<UniquePtr<ffi::Shape>> {
    if path.edges.is_empty() {
        return Err(Error::EmptyContour);
    }
    let mut builder = ffi::new_wire_builder();
    builder.pin_mut().start(
        path.start.x.value(),
        path.start.y.value(),
        path.start.z.value(),
    )?;
    for edge in &path.edges {
        add_curve3(builder.pin_mut(), edge)?;
    }
    Ok(builder.pin_mut().finish(closed)?)
}

fn add_curve3(builder: Pin<&mut ffi::WireBuilder>, edge: &Curve3) -> Result<()> {
    match edge {
        Curve3::Line { to } => builder.line(to.x.value(), to.y.value(), to.z.value())?,
        Curve3::Arc { via, to } => builder.arc(
            via.x.value(),
            via.y.value(),
            via.z.value(),
            to.x.value(),
            to.y.value(),
            to.z.value(),
        )?,
        Curve3::Spline { points } => builder.spline(&flatten3(points))?,
        Curve3::Helix {
            pitch,
            height,
            right_handed,
        } => builder.helix(pitch.value(), height.value(), *right_handed)?,
    }
    Ok(())
}

fn flatten3(points: &[Point3]) -> Vec<f64> {
    let mut flat = Vec::with_capacity(points.len() * 3);
    for point in points {
        flat.push(point.x.value());
        flat.push(point.y.value());
        flat.push(point.z.value());
    }
    flat
}

fn add_curve2(builder: Pin<&mut ffi::WireBuilder>, edge: &Curve2) -> Result<()> {
    match edge {
        Curve2::Line { to } => builder.line(to.x.value(), to.y.value(), 0.0)?,
        Curve2::Arc { via, to } => builder.arc(
            via.x.value(),
            via.y.value(),
            0.0,
            to.x.value(),
            to.y.value(),
            0.0,
        )?,
        Curve2::Spline { points } => builder.spline(&flatten2(points))?,
    }
    Ok(())
}

fn flatten2(points: &[Point2]) -> Vec<f64> {
    let mut flat = Vec::with_capacity(points.len() * 3);
    for point in points {
        flat.push(point.x.value());
        flat.push(point.y.value());
        flat.push(0.0);
    }
    flat
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::Scalar;

    fn scalar(value: f64) -> Scalar {
        Scalar::try_from(value).expect("finite")
    }

    fn box_node(size: f64) -> Node<usize> {
        Node::Box {
            width: scalar(size),
            length: scalar(size),
            height: scalar(size),
        }
    }

    fn build(
        model: &Model,
        make_cache: impl FnOnce(BrepCodec) -> crate::cache::Memory<Key, Part>,
    ) -> Vec<Part> {
        evaluate(model, make_cache).expect("builds")
    }

    fn box_part(size: f64) -> Part {
        build(
            &Model {
                version: 1,
                parts: vec![box_node(size)],
            },
            |_| crate::cache::Memory::default(),
        )
        .pop()
        .expect("one root")
    }

    #[test]
    fn identical_subtrees_are_built_once() {
        let model = Model {
            version: 1,
            parts: vec![box_node(2.0), box_node(2.0)],
        };
        let parts = build(&model, |_| crate::cache::Memory::default());
        assert_eq!(parts.len(), 2);
        assert!(
            Rc::ptr_eq(&parts[0].0, &parts[1].0),
            "the cache must reuse an identical node"
        );
    }

    #[test]
    fn engine_reads_from_disk() {
        let directory = std::env::temp_dir().join("vg3-engine-disk-test");
        let _ = std::fs::remove_dir_all(&directory);

        // Poison the key of a 2x2x2 box with a 1x1x1 box: if the engine consults the disk layer,
        // the result must be the small box.
        let model = Model {
            version: 1,
            parts: vec![box_node(2.0)],
        };
        let key = key_of(&model.parts[0], &model.parts).expect("key");
        let mut disk = crate::store::Disk::new(directory.clone(), BrepCodec);
        disk.put(&key, &box_part(1.0));

        let parts = evaluate(&model, |codec| {
            crate::store::Disk::new(directory.clone(), codec)
                .wrap_with(crate::cache::Memory::default())
        })
        .expect("builds");
        assert!(
            (parts[0].volume() - 1.0).abs() < 1e-9,
            "engine must have read the disk entry"
        );
        let _ = std::fs::remove_dir_all(&directory);
    }
}
