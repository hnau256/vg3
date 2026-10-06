//! Operations: transforms and the parameters of `fillet`/`sweep`.

use serde::{Deserialize, Serialize};

use crate::value::{Angle, Scalar, Vec2, Vec3};

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum TransformOp {
    Translate {
        value: Vec3,
    },
    Rotate {
        center: Vec3,
        axis: Vec3,
        angle: Angle,
    },
    Mirror {
        center: Vec3,
        normal: Vec3,
    },
    Scale {
        value: Vec3,
    },
    Matrix {
        m: [Scalar; 16],
    },
}

/// A planar (2D) transformation of a sketch.
#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum TransformOp2 {
    Translate {
        value: Vec2,
    },
    Rotate {
        center: Vec2,
        angle: Angle,
    },
    Mirror {
        center: Vec2,
        normal: Vec2,
    },
    Scale {
        value: Vec2,
    },
}

#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug, Default, Deserialize, Serialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(rename_all = "snake_case")]
pub enum SweepMode {
    #[default]
    Follow,
    Rigid,
}

#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug, Default, Deserialize, Serialize)]
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

/// How offset shells are joined (mirrors `GeomAbs_JoinType`).
#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug, Default, Deserialize, Serialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(rename_all = "snake_case")]
pub enum JoinKind {
    #[default]
    Arc,
    Tangent,
    Intersection,
}

/// How a sweep joins the pipe at fractures (corners) of the spine
/// (mirrors `BRepBuilderAPI_TransitionMode`).
#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug, Default, Deserialize, Serialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(rename_all = "snake_case")]
pub enum TransitionKind {
    #[default]
    RightCorner,
    Transformed,
    RoundCorner,
}

/// Continuity of a lofted surface (mirrors `GeomAbs_Shape`).
#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(rename_all = "snake_case")]
pub enum Continuity {
    C0,
    C1,
    C2,
    C3,
}

/// Parametrization of a lofted surface approximation
/// (mirrors `Approx_ParametrizationType`).
#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(rename_all = "snake_case")]
pub enum Parametrization {
    ChordLength,
    Centripetal,
    IsoParametric,
}

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum RadiusSpec {
    All {
        radius: Scalar,
    },
    /// A per-edge Rhai expression returning the radius (number) for every edge.
    Expression {
        expression: String,
    },
    /// A boolean Rhai predicate selecting edges; `radius` is applied to the selected ones.
    Selected {
        expression: String,
        radius: Scalar,
    },
}

/// Which faces of a `thick_solid` are removed (opened).
#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum FaceSelection {
    /// Faces for which the boolean Rhai predicate is true are removed.
    Selected {
        expression: String,
    },
}
