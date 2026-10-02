//! Contours: 2D/3D curves, profiles and paths.

use serde::Deserialize;

use crate::value::{NonEmpty, Point2, Point3, Scalar};

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Curve2 {
    Line { to: Point2 },
    Arc { via: Point2, to: Point2 },
    Spline { points: NonEmpty<Point2> },
}

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Curve3 {
    Line {
        to: Point3,
    },
    Arc {
        via: Point3,
        to: Point3,
    },
    Spline {
        points: NonEmpty<Point3>,
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
pub struct Profile {
    pub start: Point2,
    pub edges: NonEmpty<Curve2>,
}

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(deny_unknown_fields)]
pub struct Path {
    pub start: Point3,
    pub edges: NonEmpty<Curve3>,
}
