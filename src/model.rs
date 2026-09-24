use std::hash::Hash;

use serde::Deserialize;

use crate::error::{Error, Result};

#[derive(Clone, Copy, PartialEq, Debug, Deserialize)]
#[serde(try_from = "f64")]
pub struct Scalar(f64);

impl Scalar {
    pub fn value(self) -> f64 {
        self.0
    }
}

impl std::hash::Hash for Scalar {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.0.to_bits().hash(state);
    }
}

impl TryFrom<f64> for Scalar {
    type Error = Error;

    fn try_from(value: f64) -> Result<Self> {
        if !value.is_finite() {
            return Err(Error::NonFiniteScalar);
        }
        Ok(Scalar(if value == 0.0 { 0.0 } else { value }))
    }
}

const TAU: f64 = std::f64::consts::TAU;

#[derive(Clone, Copy, PartialEq, Debug, Deserialize)]
#[serde(try_from = "f64")]
pub struct Angle(f64);

impl Angle {
    pub fn value(self) -> f64 {
        self.0
    }
}

impl std::hash::Hash for Angle {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.0.to_bits().hash(state);
    }
}

impl TryFrom<f64> for Angle {
    type Error = Error;

    fn try_from(value: f64) -> Result<Self> {
        if !value.is_finite() {
            return Err(Error::NonFiniteScalar);
        }
        let reduced = value.rem_euclid(TAU);
        Ok(Angle(if reduced == 0.0 { 0.0 } else { reduced }))
    }
}

#[derive(Clone, Copy, PartialEq, Hash, Debug, Deserialize)]
pub struct Point2 {
    pub x: Scalar,
    pub y: Scalar,
}

#[derive(Clone, Copy, PartialEq, Hash, Debug, Deserialize)]
pub struct Point3 {
    pub x: Scalar,
    pub y: Scalar,
    pub z: Scalar,
}

#[derive(Clone, Copy, PartialEq, Hash, Debug, Deserialize)]
pub struct Vector3 {
    pub dx: Scalar,
    pub dy: Scalar,
    pub dz: Scalar,
}

#[derive(Clone, Copy, PartialEq, Hash, Debug)]
pub struct Normal3 {
    pub dx: Scalar,
    pub dy: Scalar,
    pub dz: Scalar,
}

#[derive(Deserialize)]
struct RawNormal3 {
    dx: Scalar,
    dy: Scalar,
    dz: Scalar,
}

impl Normal3 {
    pub fn new(dx: Scalar, dy: Scalar, dz: Scalar) -> Result<Self> {
        let length = (dx.value().powi(2) + dy.value().powi(2) + dz.value().powi(2)).sqrt();
        if length == 0.0 {
            return Err(Error::ZeroNormal);
        }
        Ok(Normal3 {
            dx: Scalar::try_from(dx.value() / length)?,
            dy: Scalar::try_from(dy.value() / length)?,
            dz: Scalar::try_from(dz.value() / length)?,
        })
    }
}

impl<'de> Deserialize<'de> for Normal3 {
    fn deserialize<D>(deserializer: D) -> std::result::Result<Self, D::Error>
    where
        D: serde::Deserializer<'de>,
    {
        let raw = RawNormal3::deserialize(deserializer)?;
        Normal3::new(raw.dx, raw.dy, raw.dz).map_err(serde::de::Error::custom)
    }
}

#[derive(Clone, PartialEq, Debug, Deserialize)]
#[serde(untagged)]
pub enum Operand {
    Index(usize),
    Inline(Box<Node>),
}

#[derive(Clone, PartialEq, Debug, Deserialize)]
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

#[derive(Clone, PartialEq, Debug, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Node {
    Box {
        width: Scalar,
        length: Scalar,
        height: Scalar,
    },
    Sphere {
        radius: Scalar,
    },
    Fuse {
        parts: Vec<Operand>,
    },
    Transform {
        target: Operand,
        ops: Vec<TransformOp>,
    },
}

#[derive(Clone, PartialEq, Debug, Deserialize)]
pub struct Model {
    pub version: u32,
    pub parts: Vec<Node>,
}

pub fn parse(source: &str) -> Result<Model> {
    let model: Model = serde_json::from_str(source)?;
    if model.version != 1 {
        return Err(Error::UnsupportedVersion(model.version));
    }
    Ok(model)
}
