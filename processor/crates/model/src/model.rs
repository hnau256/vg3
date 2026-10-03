//! The top-level model: a flat arena of bodies, plus parsing.

use serde::Deserialize;

use crate::error::{Error, Result};
use crate::body::Body;
use crate::value::{Color, BodyIndex};

/// An explicit export entry: which part to output, its name and (optionally) its color.
#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(deny_unknown_fields)]
pub struct Export {
    pub index: BodyIndex,
    pub name: String,
    #[serde(default)]
    pub color: Option<Color>,
}

#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[cfg_attr(
    feature = "schema",
    schemars(
        title = "vg3 IR model",
        description = "Canonical intermediate representation (version 1)."
    )
)]
#[serde(deny_unknown_fields)]
pub struct Model {
    pub version: u32,
    pub parts: Vec<Body<BodyIndex>>,
    pub export: Vec<Export>,
}

pub fn parse(source: &str) -> Result<Model> {
    let model: Model = serde_json::from_str(source)?;
    if model.version != 1 {
        return Err(Error::UnsupportedVersion(model.version));
    }
    Ok(model)
}
