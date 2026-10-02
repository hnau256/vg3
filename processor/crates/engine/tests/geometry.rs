use std::path::Path;

use vg3_engine::engine::{self, Part};

fn build_outputs(fixture: &str) -> Vec<vg3_engine::Output> {
    let path = Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("tests/fixtures")
        .join(fixture);
    let source = std::fs::read_to_string(path).expect("fixture is readable");
    let model = vg3_model::parse(&source).expect("fixture parses");
    engine::evaluate(&model, &mut vg3_cache::Noop).expect("fixture builds")
}

fn build(fixture: &str) -> Vec<Part> {
    build_outputs(fixture)
        .into_iter()
        .map(|output| output.part)
        .collect()
}

fn assert_close(actual: f64, expected: f64, tolerance: f64) {
    assert!(
        (actual - expected).abs() <= tolerance,
        "expected {expected} +/- {tolerance}, got {actual}"
    );
}

fn assert_bounds(part: &Part, expected: [f64; 6]) {
    let bounds = part.bounding_box();
    for (index, value) in expected.iter().enumerate() {
        assert_close(bounds[index], *value, 1e-6);
    }
}

#[test]
fn box_has_expected_volume_and_bounds() {
    let parts = build("box.json");
    assert_eq!(parts.len(), 1);
    assert_eq!(parts[0].solid_count(), 1);
    assert_close(parts[0].volume(), 2000.0, 1e-6);
    assert_bounds(&parts[0], [0.0, 0.0, 0.0, 20.0, 20.0, 5.0]);
}

#[test]
fn polyhedron_builds_a_unit_cube() {
    let parts = build("polyhedron.json");
    assert_eq!(parts[0].solid_count(), 1);
    assert_close(parts[0].volume(), 1.0, 1e-6);
    assert_bounds(&parts[0], [0.0, 0.0, 0.0, 1.0, 1.0, 1.0]);
}

#[test]
fn offset_grows_the_solid_in_every_direction() {
    let parts = build("offset.json");
    assert_eq!(parts[0].solid_count(), 1);
    assert!(parts[0].volume() > 1000.0);
    assert_bounds(&parts[0], [-1.0, -1.0, -1.0, 11.0, 11.0, 11.0]);
}

#[test]
fn fuse_of_box_and_sphere_is_a_single_solid() {
    let parts = build("boss.json");
    assert_eq!(parts.len(), 1);
    assert_eq!(parts[0].solid_count(), 1);
    assert!(parts[0].volume() > 2000.0);
}

#[test]
fn extrude_matches_profile_area_times_height() {
    let parts = build("extrude.json");
    assert_eq!(parts[0].solid_count(), 1);
    assert_close(parts[0].volume(), 500.0, 1e-6);
    assert_bounds(&parts[0], [0.0, 0.0, 0.0, 10.0, 10.0, 5.0]);
}

#[test]
fn circular_extrude_matches_cylinder_volume() {
    let parts = build("circle_extrude.json");
    assert_eq!(parts[0].solid_count(), 1);
    assert_close(parts[0].volume(), std::f64::consts::PI * 25.0 * 3.0, 1e-3);
    assert_bounds(&parts[0], [-5.0, -5.0, 0.0, 5.0, 5.0, 3.0]);
}

#[test]
fn full_revolve_matches_tube_volume() {
    let parts = build("revolve.json");
    assert_eq!(parts[0].solid_count(), 1);
    assert_close(parts[0].volume(), std::f64::consts::PI * 12.0 * 3.0, 1e-3);
    assert_bounds(&parts[0], [-4.0, 0.0, -4.0, 4.0, 3.0, 4.0]);
}

#[test]
fn ruled_loft_matches_frustum_volume() {
    let parts = build("loft.json");
    assert_eq!(parts[0].solid_count(), 1);
    assert_close(parts[0].volume(), 10.0 / 3.0 * 28.0, 1e-3);
    assert_bounds(&parts[0], [-2.0, -2.0, 0.0, 2.0, 2.0, 10.0]);
}

#[test]
fn follow_sweep_produces_a_solid() {
    let parts = build("sweep.json");
    assert_eq!(parts[0].solid_count(), 1);
    let volume = parts[0].volume();
    assert!(volume > 30.0 && volume < 45.0, "unexpected volume {volume}");
}

#[test]
fn rigid_sweep_along_a_line_equals_a_box() {
    let parts = build("sweep_rigid.json");
    assert_eq!(parts[0].solid_count(), 1);
    assert_close(parts[0].volume(), 40.0, 1e-3);
}

#[test]
fn fillet_removes_material_from_vertical_edges() {
    let parts = build("fillet.json");
    assert_eq!(parts[0].solid_count(), 1);
    let volume = parts[0].volume();
    assert!(
        volume > 0.0 && volume < 3000.0,
        "unexpected volume {volume}"
    );
}

#[test]
fn export_config_renders_a_png() {
    let outputs = build_outputs("box.json");
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
        "parts": [
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
        "parts": [
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
fn fuse_result_is_unified_into_a_clean_brep() {
    let parts = build("fuse_clean.json");
    assert_eq!(parts[0].solid_count(), 1);
    assert_eq!(parts[0].face_count(), 6);
    assert_close(parts[0].volume(), 2000.0, 1e-6);
    assert_bounds(&parts[0], [0.0, 0.0, 0.0, 20.0, 10.0, 10.0]);
}

#[test]
fn halfspace_cuts_away_material_on_the_reference_side() {
    let parts = build("halfspace.json");
    assert_eq!(parts.len(), 1);
    assert_eq!(parts[0].solid_count(), 1);
    assert_close(parts[0].volume(), 500.0, 1e-6);
    assert_bounds(&parts[0], [0.0, 0.0, 5.0, 10.0, 10.0, 10.0]);
}

#[test]
fn helical_thread_adds_a_ridge_to_the_cylinder() {
    let parts = build("thread.json");
    assert_eq!(parts.len(), 1);
    assert_eq!(parts[0].solid_count(), 1);
    let volume = parts[0].volume();
    let cylinder = std::f64::consts::PI * 16.0 * 10.0;
    assert!(
        volume > cylinder + 5.0 && volume < cylinder + 40.0,
        "expected a cylinder plus a thread ridge, got {volume}"
    );
}

#[test]
fn opencascade_bottle_builds() {
    let parts = build("bottle.json");
    assert_eq!(parts.len(), 1);
    assert_eq!(parts[0].solid_count(), 1);
    let bounds = parts[0].bounding_box();
    for (index, expected) in [-25.0, -15.0, 0.0, 25.0, 15.0, 77.4].iter().enumerate() {
        assert_close(bounds[index], *expected, 1e-3);
    }
    assert_close(parts[0].volume(), 89419.46, 1.0);
}

#[test]
fn chamfer_on_a_box_removes_material() {
    let parts = build("chamfer.json");
    assert_eq!(parts[0].solid_count(), 1);
    let volume = parts[0].volume();
    assert!(
        volume > 0.0 && volume < 1000.0,
        "unexpected volume {volume}"
    );
}
