//! The `vg3` IR: the domain model (a flat arena of `Node`s) and its parsing.

mod error;
mod model;

pub use error::{Error, Result};
pub use model::*;
