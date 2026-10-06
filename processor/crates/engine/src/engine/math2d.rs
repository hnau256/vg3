//! 2D vector helpers for expressions: functions over `{x, y}` maps, plus the unit axes.

use rhai::{Dynamic, Engine, Map};

use crate::engine::expression::{Context, Data};

/// Tolerance for the geometric predicates (`is_close`, `is_parallel`, …).
const TOLERANCE: f64 = 1e-7;

/// A context with the 2D vector helpers and the unit axes `X`/`Y`.
pub(super) fn context() -> Context {
    Context::new(engine())
        .with_data(Data::new("X", point(1.0, 0.0)))
        .with_data(Data::new("Y", point(0.0, 1.0)))
}

fn engine() -> Engine {
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
    engine.register_fn("vec", |x: f64, y: f64| point(x, y));
    engine.register_fn("is_close_point", is_close_point);
    engine.register_fn("add", add);
    engine.register_fn("sub", sub);
    engine.register_fn("scale", scale);
    engine
}

pub(super) fn point(x: f64, y: f64) -> Map {
    let mut map = Map::new();
    map.insert("x".into(), Dynamic::from(x));
    map.insert("y".into(), Dynamic::from(y));
    map
}

fn components(value: &Map) -> (f64, f64) {
    let component = |key: &str| {
        value
            .get(key)
            .and_then(|item| item.clone().try_cast::<f64>())
            .unwrap_or(0.0)
    };
    (component("x"), component("y"))
}

fn dot(a: Map, b: Map) -> f64 {
    let (ax, ay) = components(&a);
    let (bx, by) = components(&b);
    ax * bx + ay * by
}

/// The scalar cross product `a.x * b.y - a.y * b.x`.
fn cross(a: Map, b: Map) -> f64 {
    let (ax, ay) = components(&a);
    let (bx, by) = components(&b);
    ax * by - ay * bx
}

fn length(a: Map) -> f64 {
    let (ax, ay) = components(&a);
    (ax * ax + ay * ay).sqrt()
}

fn normalized(a: Map) -> Map {
    let (ax, ay) = components(&a);
    let magnitude = (ax * ax + ay * ay).sqrt();
    if magnitude == 0.0 {
        point(0.0, 0.0)
    } else {
        point(ax / magnitude, ay / magnitude)
    }
}

fn distance(a: Map, b: Map) -> f64 {
    let (ax, ay) = components(&a);
    let (bx, by) = components(&b);
    ((ax - bx).powi(2) + (ay - by).powi(2)).sqrt()
}

fn angle(a: Map, b: Map) -> f64 {
    let (ax, ay) = components(&normalized(a));
    let (bx, by) = components(&normalized(b));
    (ax * bx + ay * by).clamp(-1.0, 1.0).acos()
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
    let (ax, ay) = components(&a);
    let (bx, by) = components(&b);
    is_close(ax, bx) && is_close(ay, by)
}

fn add(a: Map, b: Map) -> Map {
    let (ax, ay) = components(&a);
    let (bx, by) = components(&b);
    point(ax + bx, ay + by)
}

fn sub(a: Map, b: Map) -> Map {
    let (ax, ay) = components(&a);
    let (bx, by) = components(&b);
    point(ax - bx, ay - by)
}

fn scale(a: Map, factor: f64) -> Map {
    let (ax, ay) = components(&a);
    point(ax * factor, ay * factor)
}
