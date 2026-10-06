//! Shared vector helpers for expressions.
//!
//! Points are `{x, y, z}` maps; a planar (2D) element simply has `z = 0`, so the helpers are
//! dimension-agnostic. `cross` is the only operation that differs by dimension (a scalar in 2D, a
//! vector in 3D), so each dimension registers its own; everything else is registered from here.

use rhai::{Dynamic, Engine, Map};

/// Tolerance for the geometric predicates (`is_close`, `is_parallel`, …).
pub(super) const TOLERANCE: f64 = 1e-7;

/// Registers the dimension-agnostic vector helpers on `engine`.
pub(super) fn register(engine: &mut Engine) {
    engine.register_fn("dot", dot);
    engine.register_fn("length", length);
    engine.register_fn("normalized", normalized);
    engine.register_fn("distance", distance);
    engine.register_fn("angle", angle);
    engine.register_fn("is_close", is_close);
    engine.register_fn("is_parallel", is_parallel);
    engine.register_fn("is_perpendicular", is_perpendicular);
    engine.register_fn("is_close_point", is_close_point);
    engine.register_fn("add", add);
    engine.register_fn("sub", sub);
    engine.register_fn("scale", scale);
}

pub(super) fn point(x: f64, y: f64, z: f64) -> Map {
    let mut map = Map::new();
    map.insert("x".into(), Dynamic::from(x));
    map.insert("y".into(), Dynamic::from(y));
    map.insert("z".into(), Dynamic::from(z));
    map
}

pub(super) fn components(value: &Map) -> (f64, f64, f64) {
    let component = |key: &str| {
        value
            .get(key)
            .and_then(|item| item.clone().try_cast::<f64>())
            .unwrap_or(0.0)
    };
    (component("x"), component("y"), component("z"))
}

fn dot(a: Map, b: Map) -> f64 {
    let (ax, ay, az) = components(&a);
    let (bx, by, bz) = components(&b);
    ax * bx + ay * by + az * bz
}

/// The 3D cross product.
pub(super) fn cross(a: Map, b: Map) -> Map {
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
