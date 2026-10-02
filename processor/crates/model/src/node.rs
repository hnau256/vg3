//! The IR node — a flat arena of these is the model.

use serde::Deserialize;

use crate::curve::{Path, Profile};
use crate::op::{FilletKind, RadiusSpec, SweepMode, TransformOp};
use crate::value::{Angle, Scalar};

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Node<T> {
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
        profile: Profile,
        height: Scalar,
    },
    Revolve {
        profile: Profile,
        angle: Angle,
    },
    Sweep {
        profile: Profile,
        path: Path,
        #[serde(default)]
        mode: SweepMode,
    },
    Loft {
        sections: Vec<Path>,
        #[serde(default)]
        ruled: bool,
    },
    Fuse {
        parts: Vec<T>,
    },
    Cut {
        base: T,
        tools: Vec<T>,
    },
    Common {
        parts: Vec<T>,
    },
    Transform {
        target: T,
        op: TransformOp,
    },
    Offset {
        target: T,
        distance: Scalar,
    },
    Fillet {
        target: T,
        #[serde(default)]
        kind: FilletKind,
        radius: RadiusSpec,
    },
}

impl<T: Clone> Node<T> {
    /// Functor over operands: rebuilds the node, applying `f` to every operand, in order.
    ///
    /// This is the single source of truth for "where are a node's operands". Operand-level
    /// concerns — validation, reachability, evaluation (`Node<usize>` → `Node<Part>`) and the
    /// cache key — are all expressed as a `try_map` over the arena.
    pub fn try_map<U, E>(
        &self,
        mut f: impl FnMut(T) -> std::result::Result<U, E>,
    ) -> std::result::Result<Node<U>, E> {
        Ok(match self {
            Node::Box {
                width,
                length,
                height,
            } => Node::Box {
                width: *width,
                length: *length,
                height: *height,
            },
            Node::Sphere { radius } => Node::Sphere { radius: *radius },
            Node::Cylinder { radius, height } => Node::Cylinder {
                radius: *radius,
                height: *height,
            },
            Node::Cone {
                radius_bottom,
                radius_top,
                height,
            } => Node::Cone {
                radius_bottom: *radius_bottom,
                radius_top: *radius_top,
                height: *height,
            },
            Node::Torus {
                major_radius,
                minor_radius,
            } => Node::Torus {
                major_radius: *major_radius,
                minor_radius: *minor_radius,
            },
            Node::Wedge {
                width,
                length,
                height,
                top_width,
            } => Node::Wedge {
                width: *width,
                length: *length,
                height: *height,
                top_width: *top_width,
            },
            Node::Halfspace => Node::Halfspace,
            Node::Extrude { profile, height } => Node::Extrude {
                profile: profile.clone(),
                height: *height,
            },
            Node::Revolve { profile, angle } => Node::Revolve {
                profile: profile.clone(),
                angle: *angle,
            },
            Node::Sweep {
                profile,
                path,
                mode,
            } => Node::Sweep {
                profile: profile.clone(),
                path: path.clone(),
                mode: *mode,
            },
            Node::Loft { sections, ruled } => Node::Loft {
                sections: sections.clone(),
                ruled: *ruled,
            },
            Node::Fuse { parts } => Node::Fuse {
                parts: parts
                    .iter()
                    .cloned()
                    .map(&mut f)
                    .collect::<std::result::Result<_, E>>()?,
            },
            Node::Cut { base, tools } => Node::Cut {
                base: f(base.clone())?,
                tools: tools
                    .iter()
                    .cloned()
                    .map(&mut f)
                    .collect::<std::result::Result<_, E>>()?,
            },
            Node::Common { parts } => Node::Common {
                parts: parts
                    .iter()
                    .cloned()
                    .map(&mut f)
                    .collect::<std::result::Result<_, E>>()?,
            },
            Node::Transform { target, op } => Node::Transform {
                target: f(target.clone())?,
                op: op.clone(),
            },
            Node::Offset { target, distance } => Node::Offset {
                target: f(target.clone())?,
                distance: *distance,
            },
            Node::Fillet {
                target,
                kind,
                radius,
            } => Node::Fillet {
                target: f(target.clone())?,
                kind: *kind,
                radius: radius.clone(),
            },
        })
    }
}
