use vg3_model::{FaceSelection, JoinKind};

use crate::engine::info;
use crate::engine::math3d;
use crate::engine::op::join_code;
use crate::engine::part::{make_part, Part};
use crate::error::Result;
use crate::sys::ffi;

/// Hollows `target` into a shell of wall thickness `offset`, opening the selected faces.
pub(super) fn evaluate_thick_solid(
    target: Part,
    offset: f64,
    faces: &FaceSelection,
    join: JoinKind,
) -> Result<Part> {
    let shape = target.shape();
    let context = math3d::context().with_data(info::body(shape));
    let FaceSelection::Selected { expression } = faces;
    let compiled = context.compile(expression)?;
    let mut selected = Vec::new();
    for index in 0..ffi::face_count(shape) {
        let data = ffi::face_data(shape, index);
        if context.evaluate::<bool>(&compiled, info::face(&data))? {
            selected.push(index as u32);
        }
    }
    make_part(ffi::thick_solid(shape, &selected, offset, join_code(join))?)
}
