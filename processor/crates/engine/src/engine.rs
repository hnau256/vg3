//! The `Body -> Part` transformation, and the Merkle key that the cache is keyed by.

use std::sync::OnceLock;

use vg3_cache::{get_or_put, Cache, Fingerprinter, Key};
use vg3_model::{Body, BodyIndex, Color, Model, Sketch, SketchIndex};

use crate::error::{Error, Result};
use crate::sys::ffi;

mod contour;
mod expression;
mod fillet;
mod fillet2d;
mod info;
mod math2d;
mod math3d;
mod op;
mod part;
mod radius;
mod sketch;
mod thick_solid;

pub use part::{BrepPartCodec, Part};
pub use sketch::{BrepRegionCodec, Region};

use op::Evaluate;
use sketch::build_regions;

/// An exported part: its built geometry plus the name and optional color from the `export` list.
pub struct Output {
    pub name: String,
    pub color: Option<Color>,
    pub part: Part,
}

/// Evaluates exactly what the model's `export` list names, in order, using the given caches.
///
/// The caches are domain-agnostic; the engine's own codecs (`BrepPartCodec` for `Part`,
/// `BrepRegionCodec` for `Region`) are public so the caller can build disk-backed caches with them.
pub fn evaluate<P, S>(model: &Model, parts: &mut P, sketches: &mut S) -> Result<Vec<Output>>
where
    P: Cache<Key, Part> + ?Sized,
    S: Cache<Key, Region> + ?Sized,
{
    // Validate every reference (`index < current`) up front — bodies and sketches alike.
    for (index, sketch) in model.sketches.iter().enumerate() {
        sketch.try_map(|operand| -> Result<()> { validate_index(operand.value(), index) })?;
    }
    for (index, body) in model.bodies.iter().enumerate() {
        body.try_map(|operand| -> Result<()> { validate_index(operand.value(), index) })?;
        for validated in
            body.map_sketches(|sketch| validate_index(sketch.value(), model.sketches.len()))
        {
            validated?;
        }
    }

    let sketch_keys = sketch_keys(&model.sketches)?;
    let regions = build_regions(&model.sketches, &sketch_keys, sketches)?;
    let bodies = &model.bodies;
    let mut outputs = Vec::new();
    for item in &model.export {
        let index = item.index.value();
        let body = bodies.get(index).ok_or(Error::ExportIndex {
            index,
            bodies: bodies.len(),
        })?;
        outputs.push(Output {
            name: item.name.clone(),
            color: item.color,
            part: get_or_evaluate(body, bodies, &sketch_keys, &regions, parts)?,
        });
    }
    Ok(outputs)
}

/// The whole `Body -> Part` transformation. The cache key is a purely internal detail: computed
/// here, right before use, and never leaving this function. The cache is whatever the caller
/// passed in — memory, disk, a layering of both, or nothing.
///
/// Only operations are cached ([`Body::is_cacheable`]); primitives are built directly, so a cheap
/// leaf never costs a hash lookup or a disk round-trip.
fn get_or_evaluate<P: Cache<Key, Part> + ?Sized>(
    body: &Body<BodyIndex>,
    bodies: &[Body<BodyIndex>],
    sketch_keys: &[Key],
    regions: &[Region],
    cache: &mut P,
) -> Result<Part> {
    let build = |cache: &mut P| -> Result<Part> {
        let ready: Body<Part> = body.try_map(|operand| {
            get_or_evaluate(
                &bodies[operand.value()],
                bodies,
                sketch_keys,
                regions,
                cache,
            )
        })?;
        ready.evaluate(regions)
    };
    if body.is_cacheable() {
        get_or_put(cache, &key_of(body, bodies, sketch_keys)?, build)
    } else {
        build(cache)
    }
}

/// The body's Merkle key `H(body ‖ operand_keys… ‖ sketch_keys…)`, computed on demand (keys are not
/// stored). Operands are replaced by their keys, and sketch references by a canonical slot — their
/// *content* keys are carried separately, so the key does not depend on arena indices. References
/// are `index < current` (checked in [`evaluate`]), so indexing is in range.
fn key_of(body: &Body<BodyIndex>, bodies: &[Body<BodyIndex>], sketch_keys: &[Key]) -> Result<Key> {
    let mapped: Body<Key> =
        body.try_map_canonical(|operand| key_of(&bodies[operand.value()], bodies, sketch_keys))?;
    let sketches = body
        .map_sketches(|sketch| sketch_keys[sketch.value()])
        .into_iter()
        .collect::<Vec<Key>>();
    Ok(fingerprinter().of(&BodyKeyInput {
        body: &mapped,
        sketches,
    }))
}

/// The Merkle key `H(sketch ‖ operand_keys…)` of every sketch, in order (references are
/// `index < current`, checked in [`evaluate`]), so a key depends on content, not just the index.
fn sketch_keys(sketches: &[Sketch<SketchIndex>]) -> Result<Vec<Key>> {
    let mut keys: Vec<Key> = Vec::with_capacity(sketches.len());
    for sketch in sketches {
        let mapped: Sketch<Key> =
            sketch.try_map(|operand| -> Result<Key> { Ok(keys[operand.value()]) })?;
        keys.push(fingerprinter().of(&mapped));
    }
    Ok(keys)
}

/// The hashable input of a body's key: the body (operand keys in place, sketch slots canonicalized)
/// plus the keys of the sketches it references, so a body's key depends on sketch content, not on
/// the arena indices of either.
#[derive(Hash)]
struct BodyKeyInput<'a> {
    body: &'a Body<Key>,
    sketches: Vec<Key>,
}

/// Version seed mixed into every key: the tool version plus the OpenCASCADE version, so cached
/// entries are not reused after a semantics change.
fn fingerprinter() -> &'static Fingerprinter {
    static FINGERPRINTER: OnceLock<Fingerprinter> = OnceLock::new();
    FINGERPRINTER.get_or_init(|| {
        Fingerprinter::new(format!(
            "vg3-ir1; vg3 {}; occt {}",
            env!("CARGO_PKG_VERSION"),
            ffi::occt_version()
        ))
    })
}

fn validate_index(index: usize, current: usize) -> Result<()> {
    if index >= current {
        Err(Error::InvalidReference { index, current })
    } else {
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use vg3_model::{Export, Scalar, TransformOp, Vec3};

    fn scalar(value: f64) -> Scalar {
        Scalar::try_from(value).expect("finite")
    }

    fn box_node(size: f64) -> Body<BodyIndex> {
        Body::Box {
            width: scalar(size),
            length: scalar(size),
            height: scalar(size),
        }
    }

    fn transform_node(target: usize, value: f64) -> Body<BodyIndex> {
        Body::Transform {
            target: BodyIndex::new(target),
            op: TransformOp::Translate {
                value: Vec3 {
                    x: scalar(value),
                    y: scalar(0.0),
                    z: scalar(0.0),
                },
            },
        }
    }

    fn export(index: usize) -> Export {
        Export {
            index: BodyIndex::new(index),
            name: format!("p{index}"),
            color: None,
        }
    }

    fn model(parts: Vec<Body<BodyIndex>>) -> Model {
        let export = (0..parts.len()).map(export).collect();
        Model {
            version: 1,
            sketches: Vec::new(),
            bodies: parts,
            export,
        }
    }

    fn box_part(size: f64) -> Part {
        evaluate(
            &model(vec![box_node(size)]),
            &mut vg3_cache::Memory::default(),
            &mut vg3_cache::Memory::default(),
        )
        .expect("builds")
        .pop()
        .expect("one root")
        .part
    }

    #[test]
    fn primitives_are_not_cached() {
        let outputs = evaluate(
            &model(vec![box_node(2.0), box_node(2.0)]),
            &mut vg3_cache::Memory::default(),
            &mut vg3_cache::Memory::default(),
        )
        .expect("builds");
        assert_eq!(outputs.len(), 2);
        assert!(
            !outputs[0].part.shares_storage(&outputs[1].part),
            "a primitive must not be cached"
        );
    }

    #[test]
    fn identical_operations_are_built_once() {
        let outputs = evaluate(
            &Model {
                version: 1,
                sketches: Vec::new(),
                bodies: vec![
                    box_node(2.0),
                    transform_node(0, 1.0),
                    transform_node(0, 1.0),
                ],
                export: vec![export(1), export(2)],
            },
            &mut vg3_cache::Memory::default(),
            &mut vg3_cache::Memory::default(),
        )
        .expect("builds");
        assert_eq!(outputs.len(), 2);
        assert!(
            outputs[0].part.shares_storage(&outputs[1].part),
            "the cache must reuse an identical operation"
        );
    }

    #[test]
    fn body_key_uses_sketch_content_not_index() {
        // Two identical circles sit at different indices; the two extrudes reference one each.
        // Identical geometry must yield the same key, whatever the arena positions.
        let model = vg3_model::parse(
            r#"{ "version": 1,
                 "sketches": [
                     { "type": "circle", "radius": 2 },
                     { "type": "circle", "radius": 2 }
                 ],
                 "bodies": [
                     { "type": "extrude", "profile": 0, "height": 1 },
                     { "type": "extrude", "profile": 1, "height": 1 }
                 ],
                 "export": [] }"#,
        )
        .expect("parses");
        let keys = sketch_keys(&model.sketches).expect("keys");
        assert_eq!(keys[0], keys[1], "identical sketches share a key");
        assert_eq!(
            key_of(&model.bodies[0], &model.bodies, &keys).expect("key"),
            key_of(&model.bodies[1], &model.bodies, &keys).expect("key"),
            "the body key must use the sketch content, not its arena index"
        );
    }

    #[test]
    fn engine_reads_from_disk() {
        let directory = std::env::temp_dir().join("vg3-engine-disk-test");
        let _ = std::fs::remove_dir_all(&directory);

        // Poison the key of a translated 2x2x2 box with a 1x1x1 box: if the engine consults the
        // disk layer, the result must be the small box. The root is an operation, which is cached.
        let model = Model {
            version: 1,
            sketches: Vec::new(),
            bodies: vec![box_node(2.0), transform_node(0, 5.0)],
            export: vec![export(1)],
        };
        let key = key_of(&model.bodies[1], &model.bodies, &[]).expect("key");
        let mut disk = vg3_cache::Disk::new(directory.clone(), BrepPartCodec);
        disk.put(&key, &box_part(1.0));

        let outputs = evaluate(
            &model,
            &mut vg3_cache::Disk::new(directory.clone(), BrepPartCodec)
                .wrap_with(vg3_cache::Memory::default()),
            &mut vg3_cache::Memory::default(),
        )
        .expect("builds");
        assert!(
            (outputs[0].part.volume() - 1.0).abs() < 1e-9,
            "engine must have read the disk entry"
        );
        let _ = std::fs::remove_dir_all(&directory);
    }

    /// A model with a circle, a translated copy of it and an extrude body over the copy.
    fn transformed_circle() -> Model {
        vg3_model::parse(
            r#"{ "version": 1,
                 "sketches": [
                     { "type": "circle", "radius": 2 },
                     { "type": "transform", "target": 0,
                       "op": { "type": "translate", "value": { "x": 1, "y": 0 } } }
                 ],
                 "bodies": [ { "type": "extrude", "profile": 1, "height": 1 } ],
                 "export": [ { "index": 0, "name": "p" } ] }"#,
        )
        .expect("parses")
    }

    #[test]
    fn identical_sketch_operations_are_built_once() {
        let model = vg3_model::parse(
            r#"{ "version": 1,
                 "sketches": [
                     { "type": "circle", "radius": 2 },
                     { "type": "transform", "target": 0,
                       "op": { "type": "translate", "value": { "x": 1, "y": 0 } } },
                     { "type": "transform", "target": 0,
                       "op": { "type": "translate", "value": { "x": 1, "y": 0 } } }
                 ], "bodies": [], "export": [] }"#,
        )
        .expect("parses");
        let keys = sketch_keys(&model.sketches).expect("keys");
        let regions = build_regions(&model.sketches, &keys, &mut vg3_cache::Memory::default())
            .expect("builds");
        assert!(
            regions[1].shares_storage(&regions[2]),
            "the cache must reuse an identical sketch operation"
        );
    }

    #[test]
    fn engine_reads_sketches_from_disk() {
        let directory = std::env::temp_dir().join("vg3-engine-sketch-disk-test");
        let _ = std::fs::remove_dir_all(&directory);

        // Poison the key of the translated r=2 circle with an r=1 region: the extruded body must
        // come out as the poisoned (smaller) region, volume pi rather than 4*pi.
        let model = transformed_circle();
        let keys = sketch_keys(&model.sketches).expect("keys");
        let mut disk = vg3_cache::Disk::new(directory.clone(), BrepRegionCodec);
        disk.put(
            &keys[1],
            &Region::from_shape(ffi::make_circle(1.0).expect("circle")),
        );

        let outputs = evaluate(
            &model,
            &mut vg3_cache::Memory::default(),
            &mut vg3_cache::Disk::new(directory.clone(), BrepRegionCodec)
                .wrap_with(vg3_cache::Memory::default()),
        )
        .expect("builds");
        assert!(
            (outputs[0].part.volume() - std::f64::consts::PI).abs() < 1e-6,
            "engine must have read the disk region"
        );
        let _ = std::fs::remove_dir_all(&directory);
    }
}
