//! 3D vector helpers for expressions: the shared helpers plus the unit axes `X`/`Y`/`Z`.

use rhai::Engine;

use crate::engine::expression::{Context, Data};
use crate::engine::math;

/// A context with the 3D vector helpers and the unit axes `X`/`Y`/`Z`.
pub(super) fn context() -> Context {
    let mut engine = Engine::new();
    engine.set_max_operations(10_000);
    math::register(&mut engine);
    engine.register_fn("cross", math::cross);
    engine.register_fn("vec", |x: f64, y: f64, z: f64| math::point(x, y, z));
    Context::new(engine)
        .with_data(Data::new("X", math::point(1.0, 0.0, 0.0)))
        .with_data(Data::new("Y", math::point(0.0, 1.0, 0.0)))
        .with_data(Data::new("Z", math::point(0.0, 0.0, 1.0)))
}
