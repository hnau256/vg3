use cxx::UniquePtr;
use vg3_model::RadiusSpec;

use crate::engine::info;
use crate::engine::math2d;
use crate::error::Result;
use crate::sys::ffi;

/// Rounds every corner of a planar region, calling the Rhai expression for each corner.
pub(super) fn evaluate_fillet2d(
    face: &ffi::Shape,
    radius: &RadiusSpec,
) -> Result<UniquePtr<ffi::Shape>> {
    let context = math2d::context().with_data(info::profile(face));
    let count = ffi::face_corner_count(face);
    let mut values = Vec::with_capacity(count);
    for corner in 0..count {
        let data = ffi::face_corner_data(face, corner);
        let value = match radius {
            RadiusSpec::All { radius } => radius.value(),
            RadiusSpec::Expression { expression } => {
                context.evaluate(expression, info::vertex(&data))?
            }
            RadiusSpec::Selected { expression, radius } => {
                if context.evaluate::<bool>(expression, info::vertex(&data))? {
                    radius.value()
                } else {
                    0.0
                }
            }
        };
        values.push(value);
    }
    Ok(ffi::fillet2d(face, &values)?)
}
