//! Canonical value types: scalars, angles, points, vectors and normals.

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

impl Hash for Scalar {
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

#[derive(Clone, Copy, PartialEq, Debug, Deserialize)]
#[serde(try_from = "f64")]
pub struct Angle(f64);

impl Angle {
    pub fn value(self) -> f64 {
        self.0
    }
}

impl Hash for Angle {
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
        Ok(Angle(if value == 0.0 { 0.0 } else { value }))
    }
}

#[derive(Clone, Copy, PartialEq, Hash, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Point2 {
    pub x: Scalar,
    pub y: Scalar,
}

#[derive(Clone, Copy, PartialEq, Hash, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Point3 {
    pub x: Scalar,
    pub y: Scalar,
    pub z: Scalar,
}

#[derive(Clone, Copy, PartialEq, Hash, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
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
#[serde(deny_unknown_fields)]
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
