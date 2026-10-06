//! Independent analytic oracles and invariants for the cases.
//!
//! The snapshot test (`report.rs`) only pins current behaviour. These checks recompute expected
//! properties from closed formulas (`πR²H`, `w·l·h`, `4πR²`, …) and from geometric invariants,
//! independent of OpenCASCADE — so they verify correctness, not just fixate it.

use std::f64::consts::PI;
use std::path::{Path, PathBuf};

use serde_json::Value;

fn cases_dir() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/cases")
}

fn report(case: &str) -> Value {
    let path = cases_dir().join(format!("{case}_out.json"));
    let source = std::fs::read_to_string(&path)
        .unwrap_or_else(|_| panic!("missing {path:?}; run with VG3_UPDATE_EXPECTED=1"));
    serde_json::from_str(&source).expect("report is json")
}

fn body(case: &str) -> Value {
    report(case)["bodies"][0].clone()
}

fn number(value: &Value, key: &str) -> f64 {
    value[key].as_f64().unwrap_or_else(|| panic!("{key} is a number"))
}

fn count(value: &Value, key: &str) -> u64 {
    value[key].as_u64().unwrap_or_else(|| panic!("{key} is an integer"))
}

fn bounds(value: &Value) -> [f64; 6] {
    let min = &value["bounds"]["min"];
    let max = &value["bounds"]["max"];
    [
        number(min, "x"),
        number(min, "y"),
        number(min, "z"),
        number(max, "x"),
        number(max, "y"),
        number(max, "z"),
    ]
}

fn assert_close(actual: f64, expected: f64) {
    let tolerance = 1e-6 + 1e-6 * expected.abs();
    assert!(
        (actual - expected).abs() <= tolerance,
        "expected {expected}, got {actual}"
    );
}

fn assert_bounds(value: &Value, expected: [f64; 6]) {
    let actual = bounds(value);
    for (actual, expected) in actual.iter().zip(expected) {
        assert_close(*actual, expected);
    }
}

#[test]
fn primitives_match_closed_forms() {
    let b = body("box");
    assert_close(number(&b, "volume"), 2.0 * 3.0 * 4.0);
    assert_close(number(&b, "area"), 2.0 * (2.0 * 3.0 + 2.0 * 4.0 + 3.0 * 4.0));
    assert_eq!(count(&b, "faces"), 6);
    assert_eq!(count(&b, "edges"), 12);
    assert_bounds(&b, [0.0, 0.0, 0.0, 2.0, 3.0, 4.0]);

    let b = body("sphere");
    assert_close(number(&b, "volume"), 4.0 / 3.0 * PI * 27.0);
    assert_close(number(&b, "area"), 4.0 * PI * 9.0);

    let b = body("cylinder");
    assert_close(number(&b, "volume"), PI * 4.0 * 5.0);
    assert_close(number(&b, "area"), 2.0 * PI * 4.0 + 2.0 * PI * 2.0 * 5.0);

    let b = body("cone");
    assert_close(number(&b, "volume"), PI * 4.0 / 3.0 * (9.0 + 3.0 + 1.0));

    let b = body("torus");
    assert_close(number(&b, "volume"), 2.0 * PI * PI * 5.0 * 1.0);
    assert_close(number(&b, "area"), 4.0 * PI * PI * 5.0 * 1.0);

    // MakeWedge(dx, dy, dz, ltx) tapers the top face: volume = dy·dz·(dx+ltx)/2.
    let b = body("wedge");
    assert_close(number(&b, "volume"), 5.0 * 6.0 * (4.0 + 2.0) / 2.0);

    let b = body("polyhedron");
    assert_close(number(&b, "volume"), 1.0 / 6.0);
}

#[test]
fn primitive_wedges_are_half_the_primitive() {
    assert_close(
        number(&body("sphere_wedge"), "volume"),
        0.5 * 4.0 / 3.0 * PI * 27.0,
    );
    assert_close(
        number(&body("cylinder_wedge"), "volume"),
        0.5 * PI * 4.0 * 5.0,
    );
    assert_close(
        number(&body("cone_wedge"), "volume"),
        0.5 * PI * 4.0 / 3.0 * (9.0 + 3.0 + 1.0),
    );
    assert_close(
        number(&body("torus_wedge"), "volume"),
        0.5 * 2.0 * PI * PI * 5.0 * 1.0,
    );
}

#[test]
fn generation_matches_closed_forms() {
    // extrude: a 4×3 rectangle by 5.
    let b = body("extrude");
    assert_close(number(&b, "volume"), 4.0 * 3.0 * 5.0);
    assert_close(
        number(&b, "area"),
        2.0 * 4.0 * 3.0 + (4.0 + 3.0) * 2.0 * 5.0,
    );
    assert_bounds(&b, [0.0, 0.0, 0.0, 4.0, 3.0, 5.0]);

    // revolve: the (2..4)×3 rectangle around Y → a tube.
    let b = body("revolve");
    assert_close(number(&b, "volume"), PI * (16.0 - 4.0) * 3.0);
    assert_close(number(&b, "area"), 60.0 * PI);
    assert_bounds(&b, [-4.0, 0.0, -4.0, 4.0, 3.0, 4.0]);

    // loft: triangles of area 8 and 2, height 10 → frustum volume.
    let b = body("loft");
    assert_close(
        number(&b, "volume"),
        10.0 / 3.0 * (8.0 + 2.0 + (8.0f64 * 2.0).sqrt()),
    );

    // rigid sweep of a 2×2 square along a straight 10-line = a box.
    assert_close(number(&body("sweep_rigid"), "volume"), 2.0 * 2.0 * 10.0);
}

#[test]
fn transforms_preserve_or_scale_volume() {
    let base = 2.0 * 2.0 * 2.0;
    for case in [
        "transform_translate",
        "transform_rotate",
        "transform_mirror",
        "transform_matrix",
    ] {
        assert_close(number(&body(case), "volume"), base);
    }
    // A uniform ×2 scale multiplies the volume by 2³.
    assert_close(number(&body("transform_scale"), "volume"), base * 8.0);
    assert_bounds(&body("transform_translate"), [1.0, 2.0, 3.0, 3.0, 4.0, 5.0]);
    assert_bounds(&body("transform_matrix"), [1.0, 2.0, 3.0, 3.0, 4.0, 5.0]);
}

/// Every report must be self-consistent (catches garbage, NaN, empty results).
#[test]
fn reports_are_self_consistent() {
    let mut checked = 0;
    for entry in std::fs::read_dir(cases_dir()).expect("cases dir") {
        let path = entry.expect("dir entry").path();
        if !path.to_str().unwrap().ends_with("_out.json") {
            continue;
        }
        let report: Value =
            serde_json::from_str(&std::fs::read_to_string(&path).expect("read")).expect("json");
        assert_eq!(report["version"], 1, "{path:?}");
        let bodies = report["bodies"].as_array().expect("bodies array");
        assert!(!bodies.is_empty(), "{path:?}: no bodies");

        for body in bodies {
            assert!(
                body["name"].as_str().is_some_and(|name| !name.is_empty()),
                "{path:?}: empty name"
            );
            assert!(number(body, "volume") > 0.0, "{path:?}: non-positive volume");
            assert!(number(body, "area") > 0.0, "{path:?}: non-positive area");
            assert!(count(body, "solids") >= 1, "{path:?}: no solids");
            assert!(count(body, "faces") >= 1, "{path:?}: no faces");
            assert!(count(body, "edges") >= 1, "{path:?}: no edges");
            let [min_x, min_y, min_z, max_x, max_y, max_z] = bounds(body);
            assert!(
                min_x <= max_x && min_y <= max_y && min_z <= max_z,
                "{path:?}: inverted bounds"
            );
            if let Some(color) = body.get("color") {
                for channel in ["r", "g", "b"] {
                    let value = number(color, channel);
                    assert!(
                        (0.0..=1.0).contains(&value),
                        "{path:?}: color {channel} out of range"
                    );
                }
            }
        }
        checked += 1;
    }
    assert!(checked > 0, "no reports found");
}
