//! Data-driven geometry tests.
//!
//! For every `tests/cases/<name>_in.json` (a model) this builds it, produces the `json` metadata
//! report and compares it with the expected `tests/cases/<name>_out.json`. Numbers are compared
//! with a fixed tolerance (OpenCASCADE adds a small bbox gap and float noise); everything else is
//! exact.
//!
//! Regenerate the expected snapshots after an intentional change:
//! `VG3_UPDATE_EXPECTED=1 cargo test -p vg3-engine --test report`.

use std::path::{Path, PathBuf};

use serde_json::Value;
use vg3_engine::engine;

/// Absolute and relative tolerance for report numbers.
const ABS_TOLERANCE: f64 = 1e-6;
const REL_TOLERANCE: f64 = 1e-6;

fn cases_dir() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/cases")
}

/// Builds the model and returns its `json` metadata report.
fn build_report(source: &str) -> String {
    let model = vg3_model::parse(source).expect("case parses");
    let outputs = engine::evaluate(&model, &mut vg3_cache::Noop).expect("case builds");
    let path = std::env::temp_dir().join(format!("vg3-report-{}.json", std::process::id()));
    let config = vg3_engine::export::ExportConfig::from_json(&format!(
        r#"{{ "format": "json", "filename": {:?} }}"#,
        path.to_str().unwrap()
    ))
    .expect("json config parses");
    config.export(&outputs).expect("report exports");
    let text = std::fs::read_to_string(&path).expect("report is written");
    let _ = std::fs::remove_file(&path);
    text
}

#[test]
fn geometry_reports_match_expected() {
    let update = std::env::var_os("VG3_UPDATE_EXPECTED").is_some();
    let directory = cases_dir();
    let mut checked = 0usize;
    let mut failures = Vec::new();

    let mut cases: Vec<PathBuf> = std::fs::read_dir(&directory)
        .expect("cases dir exists")
        .map(|entry| entry.expect("dir entry").path())
        .filter(|path| {
            path.file_name()
                .and_then(|name| name.to_str())
                .is_some_and(|name| name.ends_with("_in.json"))
        })
        .collect();
    cases.sort();

    for input in cases {
        let name = input.file_name().unwrap().to_str().unwrap();
        let case = name.strip_suffix("_in.json").unwrap();
        let source = std::fs::read_to_string(&input).expect("case is readable");
        let actual: Value = serde_json::from_str(&build_report(&source)).expect("report is json");

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
