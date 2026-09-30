//! Sanity check for the generator itself. The schema is not committed (it is a build artifact
//! consumed by the Kotlin frontend), so there is no baseline to diff against — Gradle regenerates
//! it from this same source on every build.

#[test]
fn render_is_valid_json_schema_with_node_definition() {
    let json: serde_json::Value =
        serde_json::from_str(&vg3_schema::render()).expect("the generator emits valid JSON");
    assert_eq!(json["type"], "object");
    assert!(
        json["$defs"]["Node"].is_object(),
        "the schema must define `Node`"
    );
}
