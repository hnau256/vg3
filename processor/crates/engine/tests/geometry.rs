//! Export tests that are not about geometry metadata — STL/PNG layout, STEP, and config
//! validation. Geometry itself is covered data-driven by `report.rs` (`tests/cases/*.json`).

use std::path::Path;

use vg3_engine::engine;

/// Builds the exported outputs of the case `tests/cases/<name>_in.json`.
fn build_outputs(case: &str) -> Vec<vg3_engine::Output> {
    let path = Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("tests/cases")
        .join(format!("{case}_in.json"));
    let source = std::fs::read_to_string(path).expect("case is readable");
    let model = vg3_model::parse(&source).expect("case parses");
    engine::evaluate(&model, &mut vg3_cache::Noop).expect("case builds")
}

#[test]
fn export_config_renders_a_png() {
    let outputs = build_outputs("box");
    let path = std::env::temp_dir().join("vg3_config_render.png");
    let config = vg3_engine::export::ExportConfig::from_json(&format!(
        r#"{{ "format": "png", "size": 64,
              "output": {{ "type": "single", "filename": {:?} }} }}"#,
        path.to_str().unwrap()
    ))
    .expect("config parses");
    config.export(&outputs).expect("renders");
    let bytes = std::fs::read(&path).expect("image exists");
    assert_eq!(
        &bytes[..8],
        &[0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A]
    );
    let width = u32::from_be_bytes([bytes[16], bytes[17], bytes[18], bytes[19]]);
    assert_eq!(width, 64);
    let _ = std::fs::remove_file(&path);
}

#[test]
fn export_config_rejects_unknown_parameters() {
    let source = r#"{ "format": "stl", "nonsense": 1,
                      "output": { "type": "single", "filename": "out.stl" } }"#;
    assert!(vg3_engine::export::ExportConfig::from_json(source).is_err());
}

fn two_named_outputs() -> Vec<vg3_engine::Output> {
    let source = r#"{
        "version": 1,
        "sketches": [],
        "bodies": [
            { "type": "box", "width": 1, "length": 1, "height": 1 },
            { "type": "sphere", "radius": 1 }
        ],
        "export": [
            { "index": 0, "name": "cube" },
            { "index": 1, "name": "ball" }
        ]
    }"#;
    let model = vg3_model::parse(source).expect("parses");
    engine::evaluate(&model, &mut vg3_cache::Noop).expect("builds")
}

#[test]
fn multi_output_writes_one_file_per_named_part() {
    let outputs = two_named_outputs();
    let directory = std::env::temp_dir().join("vg3_multi_export");
    let _ = std::fs::remove_dir_all(&directory);

    for (format, extension) in [("stl", "stl"), ("png", "png")] {
        let config = vg3_engine::export::ExportConfig::from_json(&format!(
            r#"{{ "format": "{format}",
                  "output": {{ "type": "multi", "path": {:?} }} }}"#,
            directory.to_str().unwrap()
        ))
        .expect("config parses");
        config.export(&outputs).expect("exports");
        for name in ["cube", "ball"] {
            let file = directory.join(format!("{name}.{extension}"));
            assert!(file.is_file(), "expected {file:?}");
        }
    }
    let _ = std::fs::remove_dir_all(&directory);
}

#[test]
fn multi_output_rejects_duplicate_names() {
    let source = r#"{
        "version": 1,
        "sketches": [],
        "bodies": [
            { "type": "box", "width": 1, "length": 1, "height": 1 },
            { "type": "sphere", "radius": 1 }
        ],
        "export": [
            { "index": 0, "name": "part" },
            { "index": 1, "name": "part" }
        ]
    }"#;
    let model = vg3_model::parse(source).expect("parses");
    let outputs = engine::evaluate(&model, &mut vg3_cache::Noop).expect("builds");
    let directory = std::env::temp_dir().join("vg3_multi_export_dup");
    let config = vg3_engine::export::ExportConfig::from_json(&format!(
        r#"{{ "format": "stl",
              "output": {{ "type": "multi", "path": {:?} }} }}"#,
        directory.to_str().unwrap()
    ))
    .expect("config parses");
    assert!(config.export(&outputs).is_err());
    let _ = std::fs::remove_dir_all(&directory);
}

#[test]
fn step_export_is_a_single_file() {
    let path = std::env::temp_dir().join("vg3-step-test.step");
    let _ = std::fs::remove_file(&path);
    let config = vg3_engine::ExportConfig::from_json(&format!(
        r#"{{ "format": "step", "filename": "{}" }}"#,
        path.display()
    ))
    .expect("step config parses");
    let outputs = build_outputs("bottle");
    config.export(&outputs).expect("step export succeeds");
    let contents = std::fs::read_to_string(&path).expect("step file is written");
    assert!(contents.starts_with("ISO-10303-21;"));
    assert!(contents.contains("AUTOMOTIVE_DESIGN"));
    // The bottle case has a color and a name, so STEP carries both.
    assert!(contents.contains("COLOUR_RGB"));
    assert!(contents.contains("PRODUCT('bottle'"));
    let _ = std::fs::remove_file(&path);
}
