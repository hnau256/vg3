//! 2D vector helpers for expressions: the shared helpers plus `cross` (a scalar) and the unit axes
//! `X`/`Y`. Points are `{x, y, z}` maps with `z = 0`.

use rhai::{Engine, Map};

use crate::engine::expression::{Context, Data};
use crate::engine::math;

/// A context with the 2D vector helpers and the unit axes `X`/`Y`.
pub(super) fn context() -> Context {
    let mut engine = Engine::new();
    engine.set_max_operations(10_000);
    math::register(&mut engine);
    engine.register_fn("cross", cross);
    engine.register_fn("vec", |x: f64, y: f64| math::point(x, y, 0.0));
    Context::new(engine)
        .with_data(Data::new("X", math::point(1.0, 0.0, 0.0)))
        .with_data(Data::new("Y", math::point(0.0, 1.0, 0.0)))
}

/// The scalar cross product `a.x * b.y - a.y * b.x`.
fn cross(a: Map, b: Map) -> f64 {
    let (ax, ay, _) = math::components(&a);
    let (bx, by, _) = math::components(&b);
    ax * by - ay * bx
}
