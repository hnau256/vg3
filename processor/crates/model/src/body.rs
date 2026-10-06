//! The IR body — a flat arena of these is the model.

use serde::Deserialize;

use crate::curve::Path;
use crate::op::{BooleanKind, FilletKind, RadiusSpec, SweepMode, TransformOp};
use crate::value::{Angle, NonEmpty, Scalar, SketchIndex, Vec3};

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Body<T> {
    Box {
        width: Scalar,
        length: Scalar,
        height: Scalar,
    },
    Sphere {
        radius: Scalar,
    },
    Cylinder {
        radius: Scalar,
        height: Scalar,
    },
    Cone {
        radius_bottom: Scalar,
        radius_top: Scalar,
        height: Scalar,
    },
    Torus {
        major_radius: Scalar,
        minor_radius: Scalar,
    },
    Wedge {
        width: Scalar,
        length: Scalar,
        height: Scalar,
        top_width: Scalar,
    },
    Halfspace,
    Extrude {
        profile: SketchIndex,
        height: Scalar,
    },
    Revolve {
        profile: SketchIndex,
        angle: Angle,
    },
    Sweep {
        profile: SketchIndex,
        path: Path,
        #[serde(default)]
        mode: SweepMode,
    },
    Loft {
        sections: NonEmpty<Path>,
        #[serde(default)]
        ruled: bool,
    },
    Bool {
        kind: BooleanKind,
        arguments: NonEmpty<T>,
        tools: NonEmpty<T>,
    },
    Transform {
        target: T,
        op: TransformOp,
    },
    Offset {
        target: T,
        distance: Scalar,
    },
    Polyhedron {
        points: NonEmpty<Vec3>,
        faces: NonEmpty<Vec<usize>>,
    },
    Fillet {
        target: T,
        #[serde(default)]
        kind: FilletKind,
        radius: RadiusSpec,
    },
}

impl<T: Clone> Body<T> {
    /// Maps every sketch this body references, in order, returning the results (the `SketchIndex`
    /// counterpart of `try_map`: sketches live in a separate arena and are never operands of
    /// `Body<T>`). Pure — the caller decides what to do with each result.
    ///
    /// The match is exhaustive on purpose — adding a body variant that carries a sketch will not
    /// compile until it is listed here, so a reference can never be silently dropped.
    pub fn map_sketches<U>(&self, mut f: impl FnMut(SketchIndex) -> U) -> Vec<U> {
        match self {
            Body::Extrude { profile, .. } => vec![f(*profile)],
            Body::Revolve { profile, .. } => vec![f(*profile)],
            Body::Sweep { profile, .. } => vec![f(*profile)],
            Body::Box { .. }
            | Body::Sphere { .. }
            | Body::Cylinder { .. }
            | Body::Cone { .. }
            | Body::Torus { .. }
            | Body::Wedge { .. }
            | Body::Halfspace
            | Body::Loft { .. }
            | Body::Bool { .. }
            | Body::Transform { .. }
            | Body::Offset { .. }
            | Body::Polyhedron { .. }
            | Body::Fillet { .. } => Vec::new(),
        }
    }

    /// Functor over operands: rebuilds the body, applying `f` to every operand, in order.
    ///
    /// This is the single source of truth for "where are a body's operands". BodyIndex-level
    /// concerns — validation, reachability, evaluation (`Body<usize>` → `Body<Part>`) and the
    /// cache key — are all expressed as a `try_map` over the arena.
    pub fn try_map<U, E>(
        &self,
        mut f: impl FnMut(T) -> std::result::Result<U, E>,
    ) -> std::result::Result<Body<U>, E> {
        Ok(match self {
            Body::Box {
                width,
                length,
                height,
            } => Body::Box {
                width: *width,
                length: *length,
                height: *height,
            },
            Body::Sphere { radius } => Body::Sphere { radius: *radius },
            Body::Cylinder { radius, height } => Body::Cylinder {
                radius: *radius,
                height: *height,
            },
            Body::Cone {
                radius_bottom,
                radius_top,
                height,
            } => Body::Cone {
                radius_bottom: *radius_bottom,
                radius_top: *radius_top,
                height: *height,
            },
            Body::Torus {
                major_radius,
                minor_radius,
            } => Body::Torus {
                major_radius: *major_radius,
                minor_radius: *minor_radius,
            },
            Body::Wedge {
                width,
                length,
                height,
                top_width,
            } => Body::Wedge {
                width: *width,
                length: *length,
                height: *height,
                top_width: *top_width,
            },
            Body::Halfspace => Body::Halfspace,
            Body::Extrude { profile, height } => Body::Extrude {
                profile: profile.clone(),
                height: *height,
            },
            Body::Revolve { profile, angle } => Body::Revolve {
                profile: profile.clone(),
                angle: *angle,
            },
            Body::Sweep {
                profile,
                path,
                mode,
            } => Body::Sweep {
                profile: profile.clone(),
                path: path.clone(),
                mode: *mode,
            },
            Body::Loft { sections, ruled } => Body::Loft {
                sections: sections.clone(),
                ruled: *ruled,
            },
            Body::Bool {
                kind,
                arguments,
                tools,
            } => Body::Bool {
                kind: *kind,
                arguments: arguments.try_map(&mut f)?,
                tools: tools.try_map(&mut f)?,
            },
            Body::Transform { target, op } => Body::Transform {
                target: f(target.clone())?,
                op: op.clone(),
            },
            Body::Offset { target, distance } => Body::Offset {
                target: f(target.clone())?,
                distance: *distance,
            },
            Body::Polyhedron { points, faces } => Body::Polyhedron {
                points: points.clone(),
                faces: faces.clone(),
            },
            Body::Fillet {
                target,
                kind,
                radius,
            } => Body::Fillet {
                target: f(target.clone())?,
                kind: *kind,
                radius: radius.clone(),
            },
        })
    }
}
