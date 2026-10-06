//! The `vg3` IR: the domain model (flat arenas of `Sketch`s and `Body`s), its value types, parsing.

mod body;
mod curve;
mod error;
mod model;
mod op;
mod sketch;
mod value;

pub use body::Body;
pub use curve::{Curve2, Curve3, Path};
pub use error::{Error, Result};
pub use model::{parse, Export, Model};
pub use op::{
    BooleanKind, Continuity, FaceSelection, FilletKind, JoinKind, Parametrization, RadiusSpec,
    SweepMode, TransformOp, TransformOp2, TransitionKind,
};
pub use sketch::Sketch;
pub use value::{Angle, BodyIndex, Color, NonEmpty, Scalar, SketchIndex, Vec2, Vec3};
