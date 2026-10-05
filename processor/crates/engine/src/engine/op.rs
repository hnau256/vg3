use vg3_model::{BooleanKind, Body, NonEmpty, SweepMode, TransformOp};

use crate::engine::contour::build_path_wire;
use crate::engine::fillet::evaluate_fillet;
use crate::engine::part::{make_part, Part};
use crate::engine::sketch::Region;
use crate::error::{Error, Result};
use crate::sys::ffi;

/// Applies a body's operation once all its operands are already evaluated.
pub(super) trait Evaluate {
    fn evaluate(self, regions: &[Region]) -> Result<Part>;
}

impl Evaluate for Body<Part> {
    fn evaluate(self, regions: &[Region]) -> Result<Part> {
        match self {
            Body::Box {
                width,
                length,
                height,
            } => make_part(ffi::make_box(
                width.value(),
                length.value(),
                height.value(),
            )?),
            Body::Sphere { radius } => make_part(ffi::make_sphere(radius.value())?),
            Body::Cylinder { radius, height } => {
                make_part(ffi::make_cylinder(radius.value(), height.value())?)
            }
            Body::Cone {
                radius_bottom,
                radius_top,
                height,
            } => make_part(ffi::make_cone(
                radius_bottom.value(),
                radius_top.value(),
                height.value(),
            )?),
            Body::Torus {
                major_radius,
                minor_radius,
            } => make_part(ffi::make_torus(major_radius.value(), minor_radius.value())?),
            Body::Wedge {
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
            Body::Halfspace => make_part(ffi::make_halfspace()?),
            Body::Bool {
                kind,
                arguments,
                tools,
            } => evaluate_boolean(kind, arguments, tools),
            Body::Transform { target, op } => apply_transform(target, &op),
            Body::Offset { target, distance } => {
                make_part(ffi::offset(target.shape(), distance.value())?)
            }
            Body::Polyhedron { points, faces } => {
                let positions: Vec<f64> = points
                    .iter()
                    .flat_map(|point| [point.x.value(), point.y.value(), point.z.value()])
                    .collect();
                let mut indices: Vec<u32> = Vec::new();
                let mut offsets: Vec<u32> = Vec::with_capacity(faces.len() + 1);
                offsets.push(0);
                for face in &faces {
                    for &point in face {
                        indices.push(point as u32);
                    }
                    offsets.push(indices.len() as u32);
                }
                make_part(ffi::make_polyhedron(&positions, &indices, &offsets)?)
            }
            Body::Extrude { profile, height } => {
                let face = regions[profile.value()].shape();
                make_part(ffi::extrude(face, height.value())?)
            }
            Body::Revolve { profile, angle } => {
                let face = regions[profile.value()].shape();
                make_part(ffi::revolve(face, angle.value())?)
            }
            Body::Sweep {
                profile,
                path,
                mode,
            } => {
                let profile_face = regions[profile.value()].shape();
                let spine = build_path_wire(&path, false)?;
                let follow = matches!(mode, SweepMode::Follow);
                make_part(ffi::sweep(profile_face, &spine, follow)?)
            }
            Body::Loft { sections, ruled } => {
                if sections.len() < 2 {
                    return Err(Error::LoftNeedsTwoSections);
                }
                let mut builder = ffi::new_loft_builder(ruled);
                for section in sections.iter() {
                    let wire = build_path_wire(section, true)?;
                    builder.pin_mut().add(&wire)?;
                }
                make_part(builder.pin_mut().finish()?)
            }
            Body::Fillet {
                target,
                kind,
                radius,
            } => evaluate_fillet(target, kind, &radius),
        }
    }
}

/// Reduces already-evaluated operands with a binary operation (e.g. `fuse`/`cut`/`common`).
fn evaluate_boolean(
    kind: BooleanKind,
    arguments: NonEmpty<Part>,
    tools: NonEmpty<Part>,
) -> Result<Part> {
    let kind_code = match kind {
        BooleanKind::Fuse => 0,
        BooleanKind::Cut => 1,
        BooleanKind::Common => 2,
    };
    let mut builder = ffi::new_boolean_builder(kind_code);
    for argument in arguments.iter() {
        builder.pin_mut().add_argument(argument.shape())?;
    }
    for tool in tools.iter() {
        builder.pin_mut().add_tool(tool.shape())?;
    }
    make_part(builder.pin_mut().finish()?)
}

fn apply_transform(part: Part, op: &TransformOp) -> Result<Part> {
    match op {
        TransformOp::Translate { value } => make_part(ffi::translate(
            part.shape(),
            value.x.value(),
            value.y.value(),
            value.z.value(),
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
            axis.x.value(),
            axis.y.value(),
            axis.z.value(),
            angle.value(),
        )?),
        TransformOp::Mirror { center, normal } => make_part(ffi::mirror(
            part.shape(),
            center.x.value(),
            center.y.value(),
            center.z.value(),
            normal.x.value(),
            normal.y.value(),
            normal.z.value(),
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
