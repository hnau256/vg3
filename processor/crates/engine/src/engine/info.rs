//! Data blocks for expression contexts.
//!
//! Each block is a named map bound into an expression's scope. Blocks are independent and reused
//! across operations: a body's bounds (`box`) and an edge (`edge`) are 3D; a planar region's
//! bounds (`profile`) and a corner (`vertex`) are 2D.

use rhai::{Dynamic, Map};

use crate::engine::expression::Data;
use crate::engine::math;
use crate::sys::ffi;

/// The body's bounding box, exposed as `box` (`min`/`max`/`center` 3D points).
pub(super) fn body(shape: &ffi::Shape) -> Data {
    Data::new("box", bounds_map(&ffi::bounding_box(shape)))
}

/// An edge, exposed as `edge`.
pub(super) fn edge(data: &[f64]) -> Data {
    let mut edge = Map::new();
    edge.insert("length".into(), Dynamic::from(data[0]));
    edge.insert(
        "curve_type".into(),
        Dynamic::from(curve_type_name(data[1]).to_string()),
    );
    edge.insert(
        "direction".into(),
        Dynamic::from(math::point(data[2], data[3], data[4])),
    );
    edge.insert("radius".into(), Dynamic::from(data[5]));
    edge.insert(
        "start".into(),
        Dynamic::from(math::point(data[6], data[7], data[8])),
    );
    edge.insert(
        "end".into(),
        Dynamic::from(math::point(data[9], data[10], data[11])),
    );
    edge.insert(
        "center".into(),
        Dynamic::from(math::point(data[12], data[13], data[14])),
    );
    edge.insert(
        "min".into(),
        Dynamic::from(math::point(data[15], data[16], data[17])),
    );
    edge.insert(
        "max".into(),
        Dynamic::from(math::point(data[18], data[19], data[20])),
    );
    Data::new("edge", edge)
}

/// A planar region's bounding box, exposed as `profile` (`min`/`max`/`center` 2D points).
pub(super) fn profile(shape: &ffi::Shape) -> Data {
    Data::new(
        "profile",
        bounds_map(&ffi::bounding_box(shape)),
    )
}

/// A corner vertex, exposed as `vertex` (`point`, `direction1`, `direction2`, `angle`).
pub(super) fn vertex(data: &[f64]) -> Data {
    let mut vertex = Map::new();
    vertex.insert(
        "point".into(),
        Dynamic::from(math::point(data[0], data[1], 0.0)),
    );
    vertex.insert(
        "direction1".into(),
        Dynamic::from(math::point(data[3], data[4], 0.0)),
    );
    vertex.insert(
        "direction2".into(),
        Dynamic::from(math::point(data[6], data[7], 0.0)),
    );
    vertex.insert("angle".into(), Dynamic::from(data[9]));
    Data::new("vertex", vertex)
}

/// A face, exposed as `face` (`normal`, `center`, `area`, `min`, `max`).
pub(super) fn face(data: &[f64]) -> Data {
    let mut face = Map::new();
    face.insert(
        "normal".into(),
        Dynamic::from(math::point(data[0], data[1], data[2])),
    );
    face.insert(
        "center".into(),
        Dynamic::from(math::point(data[3], data[4], data[5])),
    );
    face.insert("area".into(), Dynamic::from(data[6]));
    face.insert(
        "min".into(),
        Dynamic::from(math::point(data[7], data[8], data[9])),
    );
    face.insert(
        "max".into(),
        Dynamic::from(math::point(data[10], data[11], data[12])),
    );
    Data::new("face", face)
}

/// Builds a `min`/`max`/`center` map from a bounding box, using `point` for each corner.
fn bounds_map(bounds: &[f64]) -> Map {
    let (min_x, min_y, min_z, max_x, max_y, max_z) = (
        bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5],
    );
    let mut map = Map::new();
    map.insert("min".into(), Dynamic::from(math::point(min_x, min_y, min_z)));
    map.insert("max".into(), Dynamic::from(math::point(max_x, max_y, max_z)));
    map.insert(
        "center".into(),
        Dynamic::from(math::point(
            0.5 * (min_x + max_x),
            0.5 * (min_y + max_y),
            0.5 * (min_z + max_z),
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
