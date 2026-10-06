//! The IR body — a flat arena of these is the model.

use serde::Deserialize;

use crate::curve::Path;
use crate::op::{
    BooleanKind, Continuity, FaceSelection, FilletKind, JoinKind, Parametrization, RadiusSpec,
    SweepMode, TransformOp, TransitionKind,
};
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
        /// Wedge angle (a spherical wedge); absent = the full sphere.
        #[serde(default)]
        angle: Option<Angle>,
    },
    Cylinder {
        radius: Scalar,
        height: Scalar,
        /// Wedge angle (a cylindrical wedge); absent = the full cylinder.
        #[serde(default)]
        angle: Option<Angle>,
    },
    Cone {
        radius_bottom: Scalar,
        radius_top: Scalar,
        height: Scalar,
        /// Wedge angle (a conical wedge); absent = the full cone.
        #[serde(default)]
        angle: Option<Angle>,
    },
    Torus {
        major_radius: Scalar,
        minor_radius: Scalar,
        /// Wedge angle (a toroidal wedge); absent = the full torus.
        #[serde(default)]
        angle: Option<Angle>,
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
        #[serde(default)]
        transition: TransitionKind,
    },
    Loft {
        sections: NonEmpty<Path>,
        #[serde(default)]
        ruled: bool,
        #[serde(default)]
        smoothing: bool,
        #[serde(default)]
        continuity: Option<Continuity>,
        #[serde(default)]
        parametrization: Option<Parametrization>,
        #[serde(default)]
        max_degree: Option<u32>,
        #[serde(default)]
        skip_compatibility: bool,
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
        #[serde(default)]
        join: JoinKind,
    },
    /// Hollows `target` into a shell of wall thickness `offset`, opening the selected `faces`.
    ThickSolid {
        target: T,
        offset: Scalar,
        faces: FaceSelection,
        #[serde(default)]
        join: JoinKind,
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
            | Body::ThickSolid { .. }
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
            Body::Sphere { radius, angle } => Body::Sphere {
                radius: *radius,
                angle: *angle,
            },
            Body::Cylinder {
                radius,
                height,
                angle,
            } => Body::Cylinder {
                radius: *radius,
                height: *height,
                angle: *angle,
            },
            Body::Cone {
                radius_bottom,
                radius_top,
                height,
                angle,
            } => Body::Cone {
                radius_bottom: *radius_bottom,
                radius_top: *radius_top,
                height: *height,
                angle: *angle,
            },
            Body::Torus {
                major_radius,
                minor_radius,
                angle,
            } => Body::Torus {
                major_radius: *major_radius,
                minor_radius: *minor_radius,
                angle: *angle,
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
                transition,
            } => Body::Sweep {
                profile: profile.clone(),
                path: path.clone(),
                mode: *mode,
                transition: *transition,
            },
            Body::Loft {
                sections,
                ruled,
                smoothing,
                continuity,
                parametrization,
                max_degree,
                skip_compatibility,
            } => Body::Loft {
                sections: sections.clone(),
                ruled: *ruled,
                smoothing: *smoothing,
                continuity: *continuity,
                parametrization: *parametrization,
                max_degree: *max_degree,
                skip_compatibility: *skip_compatibility,
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
            Body::Offset {
                target,
                distance,
                join,
            } => Body::Offset {
                target: f(target.clone())?,
                distance: *distance,
                join: *join,
            },
            Body::ThickSolid {
                target,
                offset,
                faces,
                join,
            } => Body::ThickSolid {
                target: f(target.clone())?,
                offset: *offset,
                faces: faces.clone(),
                join: *join,
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
