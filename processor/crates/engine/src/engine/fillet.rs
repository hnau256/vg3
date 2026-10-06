use vg3_model::{FilletKind, RadiusSpec};

use crate::engine::info;
use crate::engine::math3d;
use crate::engine::part::{make_part, Part};
use crate::engine::radius::Radius;
use crate::error::Result;
use crate::sys::ffi;

/// Fillets or chamfers every edge of `target`, calling the Rhai expression for each edge.
pub(super) fn evaluate_fillet(target: Part, kind: FilletKind, radius: &RadiusSpec) -> Result<Part> {
    let shape = target.shape();
    let context = math3d::context().with_data(info::body(shape));
    let radius = Radius::compile(&context, radius)?;
    let mut values = Vec::new();
    for solid in 0..ffi::solid_count(shape) {
        for edge in 0..ffi::solid_edge_count(shape, solid) {
            let data = ffi::solid_edge_data(shape, solid, edge);
            values.push(radius.value(&context, info::edge(&data))?);
        }
    }
    let kind_code = match kind {
        FilletKind::Fillet => 0,
        FilletKind::Chamfer => 1,
    };
    make_part(ffi::fillet(shape, kind_code, &values)?)
}
