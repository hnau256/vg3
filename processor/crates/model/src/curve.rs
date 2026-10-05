//! Contours: 2D curves (edges of a sketch contour) and 3D paths (used by `sweep`/`loft`).

use serde::Deserialize;

use crate::value::{NonEmpty, Scalar, Vec2, Vec3};

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Curve2 {
    Line { to: Vec2 },
    Arc { via: Vec2, to: Vec2 },
    Spline { points: NonEmpty<Vec2> },
}

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Curve3 {
    Line {
        to: Vec3,
    },
    Arc {
        via: Vec3,
        to: Vec3,
    },
    Spline {
        points: NonEmpty<Vec3>,
    },
    Helix {
        pitch: Scalar,
        height: Scalar,
        #[serde(default = "default_right_handed")]
        right_handed: bool,
    },
}

fn default_right_handed() -> bool {
    true
}

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(deny_unknown_fields)]
pub struct Path {
    pub start: Vec3,
    pub edges: NonEmpty<Curve3>,
}
