use vg3::engine;
use vg3::model;

#[test]
fn unsupported_version_is_rejected() {
    let source = r#"{ "version": 2, "parts": [] }"#;
    assert!(model::parse(source).is_err());
}

#[test]
fn forward_reference_is_rejected() {
    let source = r#"{ "version": 1, "parts": [ { "type": "fuse", "parts": [0] } ] }"#;
    let model = model::parse(source).expect("parses");
    assert!(engine::evaluate(&model, &mut vg3::cache::Noop).is_err());
}

#[test]
fn empty_model_is_valid() {
    let source = r#"{ "version": 1, "parts": [] }"#;
    let model = model::parse(source).expect("parses");
    let parts = engine::evaluate(&model, &mut vg3::cache::Noop).expect("builds");
    assert!(parts.is_empty());
}

#[test]
fn unreferenced_nodes_are_all_exported() {
    let source = r#"{
        "version": 1,
        "parts": [
            { "type": "box", "width": 1, "length": 1, "height": 1 },
            { "type": "sphere", "radius": 1 }
        ]
    }"#;
    let model = model::parse(source).expect("parses");
    let parts = engine::evaluate(&model, &mut vg3::cache::Noop).expect("builds");
    assert_eq!(parts.len(), 2);
}

#[test]
fn zero_normal_is_rejected() {
    let source = r#"{
        "version": 1,
        "parts": [
            { "type": "box", "width": 1, "length": 1, "height": 1 },
            { "type": "transform",
              "target": 0,
              "ops": [ { "type": "rotate", "center": { "x": 0, "y": 0, "z": 0 },
                         "axis": { "dx": 0, "dy": 0, "dz": 0 }, "angle": 1 } ] }
        ]
    }"#;
    assert!(model::parse(source).is_err());
}
