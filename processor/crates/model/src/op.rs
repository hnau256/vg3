//! Operations: transforms and the parameters of `fillet`/`sweep`.

use serde::Deserialize;

use crate::value::{Angle, Normal3, Point3, Scalar, Vector3};

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum TransformOp {
    Translate {
        value: Vector3,
    },
    Rotate {
        center: Point3,
        axis: Normal3,
        angle: Angle,
    },
    Mirror {
        center: Point3,
        normal: Normal3,
    },
    Scale {
        x: Scalar,
        y: Scalar,
        z: Scalar,
    },
    Matrix {
        m: [Scalar; 16],
    },
}

#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug, Default, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(rename_all = "snake_case")]
pub enum SweepMode {
    #[default]
    Follow,
    Rigid,
}

#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug, Default, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(rename_all = "snake_case")]
pub enum FilletKind {
    #[default]
    Fillet,
    Chamfer,
}

/// The kind of a boolean operation (mirrors `BRepAlgoAPI_BooleanOperation`).
#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(rename_all = "snake_case")]
pub enum BooleanKind {
    Fuse,
    Cut,
    Common,
}

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum RadiusSpec {
    All { radius: Scalar },
    Expression { expression: String },
}
