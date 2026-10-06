use cxx::UniquePtr;
use vg3_model::RadiusSpec;

use crate::engine::info;
use crate::engine::math2d;
use crate::engine::radius::Radius;
use crate::error::Result;
use crate::sys::ffi;

/// Rounds every corner of a planar region, calling the Rhai expression for each corner.
pub(super) fn evaluate_fillet2d(
    face: &ffi::Shape,
    radius: &RadiusSpec,
) -> Result<UniquePtr<ffi::Shape>> {
    let context = math2d::context().with_data(info::profile(face));
    let radius = Radius::compile(&context, radius)?;
    let count = ffi::face_corner_count(face);
    let mut values = Vec::with_capacity(count);
    for corner in 0..count {
        let data = ffi::face_corner_data(face, corner);
        values.push(radius.value(&context, info::vertex(&data))?);
    }
    Ok(ffi::fillet2d(face, &values)?)
}
