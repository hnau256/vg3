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

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Curve2 {
    Line { to: Point2 },
    Arc { via: Point2, to: Point2 },
    Spline { points: Vec<Point2> },
}

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
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
        points: Vec<Point3>,
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
#[serde(deny_unknown_fields)]
pub struct Profile {
    pub start: Point2,
    pub edges: Vec<Curve2>,
}

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Path {
    pub start: Point3,
    pub edges: Vec<Curve3>,
}

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
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
#[serde(rename_all = "snake_case")]
pub enum SweepMode {
    #[default]
    Follow,
    Rigid,
}

#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug, Default, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum FilletKind {
    #[default]
    Fillet,
    Chamfer,
}

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum RadiusSpec {
    All { radius: Scalar },
    Expression { expression: String },
}

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
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
        ops: Vec<TransformOp>,
    },
    Fillet {
        target: T,
        #[serde(default)]
        kind: FilletKind,
        radius: RadiusSpec,
    },
}

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Model {
    pub version: u32,
    pub parts: Vec<Node<usize>>,
}

pub fn parse(source: &str) -> Result<Model> {
    let model: Model = serde_json::from_str(source)?;
    if model.version != 1 {
        return Err(Error::UnsupportedVersion(model.version));
    }
    Ok(model)
}

impl<T: Clone> Node<T> {
    /// Functor over operands: rebuilds the node, applying `f` to every operand, in order.
    ///
    /// This is the single source of truth for "where are a node's operands". Operand-level
    /// concerns — validation, reachability, evaluation (`Node<usize>` → `Node<Part>`) and,
    /// later, the cache key — are all expressed as a `try_map` over the arena.
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
                parts: parts.iter().cloned().map(&mut f).collect::<std::result::Result<_, E>>()?,
            },
            Node::Cut { base, tools } => Node::Cut {
                base: f(base.clone())?,
                tools: tools.iter().cloned().map(&mut f).collect::<std::result::Result<_, E>>()?,
            },
            Node::Common { parts } => Node::Common {
                parts: parts.iter().cloned().map(&mut f).collect::<std::result::Result<_, E>>()?,
            },
            Node::Transform { target, ops } => Node::Transform {
                target: f(target.clone())?,
                ops: ops.clone(),
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
