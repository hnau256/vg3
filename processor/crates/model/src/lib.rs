//! The `vg3` IR: the domain model (a flat arena of `Node`s), its value/contour types, and parsing.

mod curve;
mod error;
mod model;
mod node;
mod op;
mod value;

pub use curve::{Curve2, Curve3, Path, Profile};
pub use error::{Error, Result};
pub use model::{parse, Export, Model};
pub use node::Node;
pub use op::{FilletKind, RadiusSpec, SweepMode, TransformOp};
pub use value::{Angle, Color, Normal3, Operand, Point2, Point3, Scalar, Vector3};
