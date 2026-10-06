//! Generates the canonical JSON Schema for the vg3 IR from the `vg3-model` types.
//!
//! The model crate is the single source of truth: the schema is derived from the same serde
//! attributes that drive deserialization, so it cannot drift from the wire format.

use std::path::PathBuf;

/// Renders the canonical IR JSON Schema (pretty-printed, trailing newline).
pub fn render() -> String {
    let schema = schemars::schema_for!(vg3_model::Model);
    format!(
        "{}\n",
        serde_json::to_string_pretty(&schema).expect("the schema always serializes to JSON")
    )
}

/// The generated schema path: `<repo>/scheme/vg3.schema.json` (a build artifact, not committed).
pub fn output_path() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../../scheme/vg3.schema.json")
}
