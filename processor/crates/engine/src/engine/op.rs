use cxx::UniquePtr;

use vg3_model::{Node, SweepMode, TransformOp};

use crate::engine::contour::{build_path_wire, build_profile_wire};
use crate::engine::fillet::evaluate_fillet;
use crate::engine::part::{make_part, Part};
use crate::error::{Error, Result};
use crate::sys::ffi;

/// Applies a node's operation once all its operands are already evaluated.
pub(super) trait Evaluate {
    fn evaluate(self) -> Result<Part>;
}

impl Evaluate for Node<Part> {
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
            Node::Transform { target, op } => apply_transform(target, &op),
            Node::Offset { target, distance } => {
                make_part(ffi::offset(target.shape(), distance.value())?)
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
