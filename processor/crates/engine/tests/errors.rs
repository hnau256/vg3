use vg3_engine::engine;
use vg3_model as model;

#[test]
fn unsupported_version_is_rejected() {
    let source = r#"{ "version": 2, "sketches": [], "bodies": [], "export": [] }"#;
    assert!(model::parse(source).is_err());
}

#[test]
fn forward_reference_is_rejected() {
    let source = r#"{ "version": 1, "sketches": [],
        "bodies": [ { "type": "bool", "kind": "fuse", "arguments": [0], "tools": [0] } ], "export": [] }"#;
    let model = model::parse(source).expect("parses");
    assert!(engine::evaluate(&model, &mut vg3_cache::Noop).is_err());
}

#[test]
fn empty_model_is_valid() {
    let source = r#"{ "version": 1, "sketches": [], "bodies": [], "export": [] }"#;
    let model = model::parse(source).expect("parses");
    let outputs = engine::evaluate(&model, &mut vg3_cache::Noop).expect("builds");
    assert!(outputs.is_empty());
}

#[test]
fn explicit_export_selects_what_is_built() {
    let source = r#"{
        "version": 1,
        "sketches": [],
        "bodies": [
            { "type": "box", "width": 1, "length": 1, "height": 1 },
            { "type": "sphere", "radius": 1 }
        ],
        "export": [
            { "index": 1, "name": "ball" },
            { "index": 0, "name": "cube" }
        ]
    }"#;
    let model = model::parse(source).expect("parses");
    let outputs = engine::evaluate(&model, &mut vg3_cache::Noop).expect("builds");
    assert_eq!(outputs.len(), 2);
    assert_eq!(outputs[0].name, "ball");
    assert_eq!(outputs[1].name, "cube");
}

#[test]
fn out_of_range_export_index_is_rejected() {
    let source = r#"{
        "version": 1,
        "sketches": [],
        "bodies": [ { "type": "box", "width": 1, "length": 1, "height": 1 } ],
        "export": [ { "index": 5, "name": "nope" } ]
    }"#;
    let model = model::parse(source).expect("parses");
    assert!(engine::evaluate(&model, &mut vg3_cache::Noop).is_err());
}
