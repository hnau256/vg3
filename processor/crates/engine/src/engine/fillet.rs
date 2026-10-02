use rhai::{Dynamic, Engine, Map, Scope};
use vg3_model::{FilletKind, RadiusSpec};

use crate::engine::part::{make_part, Part};
use crate::error::{Error, Result};
use crate::sys::ffi;

/// Tolerance for the edge-selection expression (`is_close`, `is_parallel`, …).
const TOLERANCE: f64 = 1e-7;

/// Fillets or chamfers every edge of `target`, calling the Rhai expression for each edge.
pub(super) fn evaluate_fillet(target: Part, kind: FilletKind, radius: &RadiusSpec) -> Result<Part> {
    let shape = target.shape();
    let engine = expression_engine();
    let box_map = bounding_box_map(shape);
    let mut values = Vec::new();
    for solid in 0..ffi::solid_count(shape) {
        for edge in 0..ffi::solid_edge_count(shape, solid) {
            let data = ffi::solid_edge_data(shape, solid, edge);
            let value = match radius {
                RadiusSpec::All { radius } => radius.value(),
                RadiusSpec::Expression { expression } => {
                    evaluate_expression(&engine, expression, &data, &box_map)?
                }
            };
            values.push(value);
        }
    }
    let kind_code = match kind {
        FilletKind::Fillet => 0,
        FilletKind::Chamfer => 1,
    };
    make_part(ffi::fillet(shape, kind_code, &values)?)
}

/// A Rhai engine with the geometric helpers used by edge-selection expressions.
///
/// The scope exposes `edge` (curve), `box` (the target's bounding box), the unit axes `X`/`Y`/`Z`,
/// and the low-level helpers (`dot`, `cross`, `length`, `normalized`, `distance`, `angle`,
/// `is_close`, `is_parallel`, `is_perpendicular`, `vec`, `is_close_point`, `add`, `sub`, `scale`).
fn expression_engine() -> Engine {
    let mut engine = Engine::new();
    engine.set_max_operations(10_000);
    engine.register_fn("dot", dot);
    engine.register_fn("cross", cross);
    engine.register_fn("length", length);
    engine.register_fn("normalized", normalized);
    engine.register_fn("distance", distance);
    engine.register_fn("angle", angle);
    engine.register_fn("is_close", is_close);
    engine.register_fn("is_parallel", is_parallel);
    engine.register_fn("is_perpendicular", is_perpendicular);
    engine.register_fn("vec", |x: f64, y: f64, z: f64| point(x, y, z));
    engine.register_fn("is_close_point", is_close_point);
    engine.register_fn("add", add);
    engine.register_fn("sub", sub);
    engine.register_fn("scale", scale);
    engine
}

fn evaluate_expression(
    engine: &Engine,
    expression: &str,
    data: &[f64],
    box_map: &Map,
) -> Result<f64> {
    let mut scope = Scope::new();
    scope.push_constant("edge", build_edge(data));
    scope.push_constant("box", box_map.clone());
    scope.push_constant("X", point(1.0, 0.0, 0.0));
    scope.push_constant("Y", point(0.0, 1.0, 0.0));
    scope.push_constant("Z", point(0.0, 0.0, 1.0));

    let result = engine
        .eval_expression_with_scope::<Dynamic>(&mut scope, expression)
        .map_err(|error| Error::Expression(format!("{expression}: {error}")))?;
    let value = if let Some(number) = result.clone().try_cast::<f64>() {
        number
    } else if let Some(number) = result.clone().try_cast::<i64>() {
        number as f64
    } else {
        return Err(Error::Expression(format!(
            "{expression}: result is not a number"
        )));
    };
    if value.is_nan() {
        return Err(Error::Expression(format!("{expression}: result is NaN")));
    }
    Ok(value)
}

// --- Scope construction -----------------------------------------------------

fn build_edge(data: &[f64]) -> Map {
    let mut edge = Map::new();
    edge.insert("length".into(), Dynamic::from(data[0]));
    edge.insert(
        "curve_type".into(),
        Dynamic::from(curve_type_name(data[1]).to_string()),
    );
    edge.insert("direction".into(), Dynamic::from(point(data[2], data[3], data[4])));
    edge.insert("radius".into(), Dynamic::from(data[5]));
    edge.insert("start".into(), Dynamic::from(point(data[6], data[7], data[8])));
    edge.insert("end".into(), Dynamic::from(point(data[9], data[10], data[11])));
    edge.insert("center".into(), Dynamic::from(point(data[12], data[13], data[14])));
    edge.insert("min".into(), Dynamic::from(point(data[15], data[16], data[17])));
    edge.insert("max".into(), Dynamic::from(point(data[18], data[19], data[20])));
    edge
}

fn bounding_box_map(shape: &ffi::Shape) -> Map {
    let bounds = ffi::bounding_box(shape);
    let mut map = Map::new();
    map.insert("min".into(), Dynamic::from(point(bounds[0], bounds[1], bounds[2])));
    map.insert("max".into(), Dynamic::from(point(bounds[3], bounds[4], bounds[5])));
    map.insert(
        "center".into(),
        Dynamic::from(point(
            0.5 * (bounds[0] + bounds[3]),
            0.5 * (bounds[1] + bounds[4]),
            0.5 * (bounds[2] + bounds[5]),
        )),
    );
    map
}

fn curve_type_name(code: f64) -> &'static str {
    match code as i32 {
        0 => "line",
        1 => "circle",
        2 => "ellipse",
        3 => "hyperbola",
        4 => "parabola",
        5 => "bezier",
        6 => "bspline",
        7 => "offset",
        _ => "other",
    }
}

fn point(x: f64, y: f64, z: f64) -> Map {
    let mut map = Map::new();
    map.insert("x".into(), Dynamic::from(x));
    map.insert("y".into(), Dynamic::from(y));
    map.insert("z".into(), Dynamic::from(z));
    map
}

fn components(value: &Map) -> (f64, f64, f64) {
    let component = |key: &str| {
        value
            .get(key)
            .and_then(|item| item.clone().try_cast::<f64>())
            .unwrap_or(0.0)
    };
    (component("x"), component("y"), component("z"))
}

// --- Low-level geometric helpers --------------------------------------------

fn dot(a: Map, b: Map) -> f64 {
    let (ax, ay, az) = components(&a);
    let (bx, by, bz) = components(&b);
    ax * bx + ay * by + az * bz
}

fn cross(a: Map, b: Map) -> Map {
    let (ax, ay, az) = components(&a);
    let (bx, by, bz) = components(&b);
    point(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx)
}

fn length(a: Map) -> f64 {
    let (ax, ay, az) = components(&a);
    (ax * ax + ay * ay + az * az).sqrt()
}

fn normalized(a: Map) -> Map {
    let (ax, ay, az) = components(&a);
    let magnitude = (ax * ax + ay * ay + az * az).sqrt();
    if magnitude == 0.0 {
        point(0.0, 0.0, 0.0)
    } else {
        point(ax / magnitude, ay / magnitude, az / magnitude)
    }
}

fn distance(a: Map, b: Map) -> f64 {
    let (ax, ay, az) = components(&a);
    let (bx, by, bz) = components(&b);
    ((ax - bx).powi(2) + (ay - by).powi(2) + (az - bz).powi(2)).sqrt()
}

fn angle(a: Map, b: Map) -> f64 {
    let (ax, ay, az) = components(&normalized(a));
    let (bx, by, bz) = components(&normalized(b));
    (ax * bx + ay * by + az * bz).clamp(-1.0, 1.0).acos()
}

fn is_close(a: f64, b: f64) -> bool {
    (a - b).abs() <= TOLERANCE
}

fn is_parallel(a: Map, b: Map) -> bool {
    dot(normalized(a), normalized(b)).abs() >= 1.0 - TOLERANCE
}

fn is_perpendicular(a: Map, b: Map) -> bool {
    dot(normalized(a), normalized(b)).abs() <= TOLERANCE
}

fn is_close_point(a: Map, b: Map) -> bool {
    let (ax, ay, az) = components(&a);
    let (bx, by, bz) = components(&b);
    is_close(ax, bx) && is_close(ay, by) && is_close(az, bz)
}

fn add(a: Map, b: Map) -> Map {
    let (ax, ay, az) = components(&a);
    let (bx, by, bz) = components(&b);
    point(ax + bx, ay + by, az + bz)
}

fn sub(a: Map, b: Map) -> Map {
    let (ax, ay, az) = components(&a);
    let (bx, by, bz) = components(&b);
    point(ax - bx, ay - by, az - bz)
}

fn scale(a: Map, factor: f64) -> Map {
    let (ax, ay, az) = components(&a);
    point(ax * factor, ay * factor, az * factor)
}
