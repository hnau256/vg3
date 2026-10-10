//! Data-driven geometry tests.
//!
//! For every `tests/cases/<name>_in.json` (a model) this builds it, produces the `json` metadata
//! report and compares it with the expected `tests/cases/<name>_out.json`. Numbers are compared
//! with a fixed tolerance (OpenCASCADE adds a small bbox gap and float noise); everything else is
//! exact.
//!
//! A second test guards coverage: the cases must exercise every node type and operation variant of
//! the IR, so a new feature cannot be added without a case.
//!
//! Regenerate the expected snapshots after an intentional change:
//! `VG3_UPDATE_EXPECTED=1 cargo test -p vg3-engine --test report`.

use std::collections::HashSet;
use std::path::{Path, PathBuf};

use serde_json::Value;
use vg3_engine::engine;

/// Absolute and relative tolerance for report numbers.
const ABS_TOLERANCE: f64 = 1e-6;
const REL_TOLERANCE: f64 = 1e-6;

/// IR node discriminators (`type`) that must appear in some case.
const REQUIRED_TYPES: &[&str] = &[
    // Body
    "box", "sphere", "cylinder", "cone", "torus", "wedge", "halfspace", "extrude", "revolve",
    "sweep", "loft", "bool", "transform", "offset", "thick_solid", "polyhedron", "fillet",
    // Sketch
    "circle", "polygon", "contour", "fillet2d", "offset2d",
    // Curve
    "line", "arc", "spline", "bezier", "helix",
    // TransformOp / TransformOp2
    "translate", "rotate", "mirror", "scale", "matrix",
    // RadiusSpec
    "all", "expression", "selected",
];

/// Enum-valued operations (key, value) that must appear explicitly in some case. Only non-default
/// variants are listed: a default variant (e.g. `FilletKind::Fillet`, `TransitionKind::RightCorner`)
/// is covered implicitly by every case that uses the node, since the canonical JSON omits defaults.
/// `TransitionKind::Transformed` and 3D `JoinKind::Tangent` are not listed: OpenCASCADE cannot build
/// them here (tangent is covered by the 2D `offset2d` case).
const REQUIRED_VALUES: &[(&str, &str)] = &[
    ("kind", "fuse"),
    ("kind", "cut"),
    ("kind", "common"),
    ("kind", "chamfer"),
    ("mode", "rigid"),
    ("join", "tangent"),
    ("join", "intersection"),
    ("transition", "round_corner"),
];

fn cases_dir() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/cases")
}

/// The `<name>_in.json` case inputs, sorted by name.
fn case_inputs() -> Vec<PathBuf> {
    let mut inputs: Vec<PathBuf> = std::fs::read_dir(cases_dir())
        .expect("cases dir exists")
        .map(|entry| entry.expect("dir entry").path())
        .filter(|path| {
            path.file_name()
                .and_then(|name| name.to_str())
                .is_some_and(|name| name.ends_with("_in.json"))
        })
        .collect();
    inputs.sort();
    inputs
}

/// Builds the model and returns its `json` metadata report.
fn build_report(source: &str) -> Result<String, String> {
    let model = vg3_model::parse(source).map_err(|error| error.to_string())?;
    let outputs = engine::evaluate(&model, &mut vg3_cache::Noop, &mut vg3_cache::Noop)
        .map_err(|error| error.to_string())?;
    let path = std::env::temp_dir().join(format!("vg3-report-{}.json", std::process::id()));
    let config = vg3_engine::export::ExportConfig::from_json(&format!(
        r#"{{ "format": "json", "filename": {:?} }}"#,
        path.to_str().unwrap()
    ))
    .expect("json config parses");
    config.export(&outputs).map_err(|error| error.to_string())?;
    let text = std::fs::read_to_string(&path).map_err(|error| error.to_string())?;
    let _ = std::fs::remove_file(&path);
    Ok(text)
}

#[test]
fn geometry_reports_match_expected() {
    let update = std::env::var_os("VG3_UPDATE_EXPECTED").is_some();
    let directory = cases_dir();
    let mut checked = 0usize;
    let mut failures = Vec::new();

    for input in case_inputs() {
        let name = input.file_name().unwrap().to_str().unwrap();
        let case = name.strip_suffix("_in.json").unwrap();
        let source = std::fs::read_to_string(&input).expect("case is readable");
        let actual_text = match build_report(&source) {
            Ok(text) => text,
            Err(error) => {
                failures.push(format!("{case}: build failed: {error}"));
                checked += 1;
                continue;
            }
        };
        let actual: Value = serde_json::from_str(&actual_text).expect("report is json");

        let expected_path = directory.join(format!("{case}_out.json"));
        if update {
            let pretty = serde_json::to_string_pretty(&actual).expect("serialize") + "\n";
            std::fs::write(&expected_path, pretty).expect("write expected");
        }
        let expected_source = std::fs::read_to_string(&expected_path).unwrap_or_else(|_| {
            panic!("missing {expected_path:?}; run with VG3_UPDATE_EXPECTED=1")
        });
        let expected: Value = serde_json::from_str(&expected_source).expect("expected is json");

        if let Err(error) = compare(&expected, &actual, case) {
            failures.push(error);
        }
        checked += 1;
    }

    assert!(checked > 0, "no cases found in {directory:?}");
    assert!(
        failures.is_empty(),
        "{} of {checked} report(s) differ:\n{}",
        failures.len(),
        failures.join("\n")
    );
}

#[test]
fn cases_cover_the_model() {
    let mut types = HashSet::new();
    let mut values = HashSet::new();
    for input in case_inputs() {
        let source = std::fs::read_to_string(&input).expect("case is readable");
        let json: Value = serde_json::from_str(&source).expect("case is json");
        collect(&json, &mut types, &mut values);
    }

    let missing: Vec<&str> = REQUIRED_TYPES
        .iter()
        .copied()
        .filter(|name| !types.contains(*name))
        .collect();
    assert!(missing.is_empty(), "node types without a case: {missing:?}");

    for (key, value) in REQUIRED_VALUES {
        assert!(
            values.contains(&(key.to_string(), value.to_string())),
            "operation `{key}: {value}` has no case"
        );
    }
}

/// Collects every `type` discriminator and every operation-enum value.
fn collect(value: &Value, types: &mut HashSet<String>, values: &mut HashSet<(String, String)>) {
    match value {
        Value::Object(map) => {
            for (key, value) in map {
                if let Value::String(text) = value {
                    if key == "type" {
                        types.insert(text.clone());
                    } else if matches!(key.as_str(), "kind" | "mode" | "join" | "transition") {
                        values.insert((key.clone(), text.clone()));
                    }
                }
                collect(value, types, values);
            }
        }
        Value::Array(items) => {
            for item in items {
                collect(item, types, values);
            }
        }
        _ => {}
    }
}

/// Compares two JSON values: numbers within tolerance, everything else exactly.
fn compare(expected: &Value, actual: &Value, path: &str) -> Result<(), String> {
    match (expected, actual) {
        (Value::Number(expected), Value::Number(actual)) => {
            let expected = expected.as_f64().expect("number");
            let actual = actual.as_f64().expect("number");
            let tolerance = ABS_TOLERANCE + REL_TOLERANCE * expected.abs().max(actual.abs());
            if (expected - actual).abs() <= tolerance {
                Ok(())
            } else {
                Err(format!("{path}: expected {expected}, got {actual}"))
            }
        }
        (Value::Object(expected), Value::Object(actual)) => {
            for (key, value) in expected {
                let actual = actual
                    .get(key)
                    .ok_or_else(|| format!("{path}.{key}: missing"))?;
                compare(value, actual, &format!("{path}.{key}"))?;
            }
            for key in actual.keys() {
                if !expected.contains_key(key) {
                    return Err(format!("{path}.{key}: unexpected"));
                }
            }
            Ok(())
        }
        (Value::Array(expected), Value::Array(actual)) => {
            if expected.len() != actual.len() {
                return Err(format!(
                    "{path}: length {} != {}",
                    expected.len(),
                    actual.len()
                ));
            }
            for (index, (value, actual)) in expected.iter().zip(actual).enumerate() {
                compare(value, actual, &format!("{path}[{index}]"))?;
            }
            Ok(())
        }
        (expected, actual) if expected == actual => Ok(()),
        (expected, actual) => Err(format!("{path}: expected {expected}, got {actual}")),
    }
}
