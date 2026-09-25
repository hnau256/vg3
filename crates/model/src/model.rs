//! The top-level model: a flat arena of nodes, plus parsing.

use serde::Deserialize;

use crate::error::{Error, Result};
use crate::node::Node;

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
