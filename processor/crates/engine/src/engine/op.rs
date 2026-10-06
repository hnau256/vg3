use vg3_model::{
    Angle, BooleanKind, Body, Continuity, JoinKind, NonEmpty, Parametrization, SweepMode,
    TransformOp, TransitionKind,
};

use crate::engine::contour::build_path_wire;
use crate::engine::fillet::evaluate_fillet;
use crate::engine::thick_solid::evaluate_thick_solid;
use crate::engine::part::{make_part, Part};
use crate::engine::sketch::Region;
use crate::error::{Error, Result};
use crate::sys::ffi;

/// A primitive's optional wedge angle as the native sentinel (`<= 0` = the full primitive).
pub(super) fn angle_value(angle: Option<Angle>) -> f64 {
    angle.map_or(0.0, |angle| angle.value())
}

/// The native code of a join kind (`GeomAbs_JoinType`).
pub(super) fn join_code(join: JoinKind) -> u8 {
    match join {
        JoinKind::Arc => 0,
        JoinKind::Tangent => 1,
        JoinKind::Intersection => 2,
    }
}

/// The native code of a boolean kind (`0` = fuse, `1` = cut, `2` = common).
pub(super) fn boolean_code(kind: BooleanKind) -> u8 {
    match kind {
        BooleanKind::Fuse => 0,
        BooleanKind::Cut => 1,
        BooleanKind::Common => 2,
    }
}

/// The native code of a sweep transition kind (`BRepBuilderAPI_TransitionMode`).
fn transition_code(transition: TransitionKind) -> u8 {
    match transition {
        TransitionKind::RightCorner => 0,
        TransitionKind::Transformed => 1,
        TransitionKind::RoundCorner => 2,
    }
}

/// The native code of an optional loft continuity (`0..3` = C0..C3; unset = `0xff`).
fn continuity_code(continuity: Option<Continuity>) -> u8 {
    match continuity {
        None => 0xff,
        Some(Continuity::C0) => 0,
        Some(Continuity::C1) => 1,
        Some(Continuity::C2) => 2,
        Some(Continuity::C3) => 3,
    }
}

/// The native code of an optional loft parametrization (`Approx_ParametrizationType`; unset = `0xff`).
fn parametrization_code(parametrization: Option<Parametrization>) -> u8 {
    match parametrization {
        None => 0xff,
        Some(Parametrization::ChordLength) => 0,
        Some(Parametrization::Centripetal) => 1,
        Some(Parametrization::IsoParametric) => 2,
    }
}

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
            Body::Sphere { radius, angle } => {
                make_part(ffi::make_sphere(radius.value(), angle_value(angle))?)
            }
            Body::Cylinder {
                radius,
                height,
                angle,
            } => make_part(ffi::make_cylinder(
                radius.value(),
                height.value(),
                angle_value(angle),
            )?),
            Body::Cone {
                radius_bottom,
                radius_top,
                height,
                angle,
            } => make_part(ffi::make_cone(
                radius_bottom.value(),
                radius_top.value(),
                height.value(),
                angle_value(angle),
            )?),
            Body::Torus {
                major_radius,
                minor_radius,
                angle,
            } => make_part(ffi::make_torus(
                major_radius.value(),
                minor_radius.value(),
                angle_value(angle),
            )?),
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
            Body::Offset {
                target,
                distance,
                join,
            } => make_part(ffi::offset(target.shape(), distance.value(), join_code(join))?),
            Body::ThickSolid {
                target,
                offset,
                faces,
                join,
            } => evaluate_thick_solid(target, offset.value(), &faces, join),
            Body::Polyhedron { points, faces } => {
                let positions: Vec<f64> = points
                    .iter()
                    .flat_map(|point| [point.x.value(), point.y.value(), point.z.value()])
                    .collect();
                let mut indices: Vec<u32> = Vec::new();
                let mut offsets: Vec<u32> = Vec::with_capacity(faces.len() + 1);
                offsets.push(0);
                for face in faces.iter() {
                    for &point in face.iter() {
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
                transition,
            } => {
                let profile_face = regions[profile.value()].shape();
                let spine = build_path_wire(&path, false)?;
                let follow = matches!(mode, SweepMode::Follow);
                make_part(ffi::sweep(
                    profile_face,
                    &spine,
                    follow,
                    transition_code(transition),
                )?)
            }
            Body::Loft {
                sections,
                ruled,
                smoothing,
                continuity,
                parametrization,
                max_degree,
                skip_compatibility,
            } => {
                if sections.len() < 2 {
                    return Err(Error::LoftNeedsTwoSections);
                }
                let mut builder = ffi::new_loft_builder(
                    ruled,
                    smoothing,
                    continuity_code(continuity),
                    parametrization_code(parametrization),
                    max_degree.map_or(0, |degree| degree as i32),
                    !skip_compatibility,
                );
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
    let kind_code = boolean_code(kind);
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
        TransformOp::Scale { value } => make_part(ffi::scale(
            part.shape(),
            value.x.value(),
            value.y.value(),
            value.z.value(),
        )?),
        TransformOp::Matrix { m } => {
            let values: Vec<f64> = m.iter().map(|scalar| scalar.value()).collect();
            make_part(ffi::apply_matrix(part.shape(), &values)?)
        }
    }
}
