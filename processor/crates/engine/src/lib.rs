//! The `vg3` engine: turns the IR into OpenCASCADE shapes and exports them.

mod error;
mod sys;

pub mod engine;
pub mod export;
pub mod render;

pub use engine::{evaluate, BrepPartCodec, BrepRegionCodec, Output, Part, Region};
pub use error::{Error, Result};
pub use export::{ExportConfig, ExportLayout};
