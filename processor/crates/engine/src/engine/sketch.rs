//! The `Sketch -> Region` transformation: a planar face per sketch node.

use cxx::UniquePtr;

use vg3_model::{BooleanKind, Sketch, SketchIndex, TransformOp2};

use crate::engine::contour::build_contour_wire;
use crate::error::Result;
use crate::sys::ffi;

/// A built planar region (a face). The counterpart of [`crate::engine::Part`] for sketches.
pub(super) struct Region {
    shape: UniquePtr<ffi::Shape>,
}

impl Region {
    pub(super) fn shape(&self) -> &ffi::Shape {
        self.shape.as_ref().expect("shape handle is never null")
    }
}

/// Builds every sketch in the arena, in order (references are `index < current`).
pub(super) fn build_regions(sketches: &[Sketch<SketchIndex>]) -> Result<Vec<Region>> {
    let mut regions: Vec<Region> = Vec::with_capacity(sketches.len());
    for sketch in sketches {
        regions.push(evaluate_sketch(sketch, &regions)?);
    }
    Ok(regions)
}

fn evaluate_sketch(sketch: &Sketch<SketchIndex>, regions: &[Region]) -> Result<Region> {
    match sketch {
        Sketch::Polygon { points } => {
            let mut iter = points.iter();
            let first = iter.next().expect("NonEmpty is non-empty");
            let mut builder = ffi::new_wire_builder();
            builder
                .pin_mut()
                .start(first.x.value(), first.y.value(), 0.0)?;
            for point in iter {
                builder
                    .pin_mut()
                    .line(point.x.value(), point.y.value(), 0.0)?;
            }
            from_wire(builder.pin_mut().finish(true)?)
        }
        Sketch::Contour { start, edges } => {
            from_wire(build_contour_wire(*start, edges)?)
        }
        Sketch::Bool {
            kind,
            arguments,
            tools,
        } => {
            let kind_code = match kind {
                BooleanKind::Fuse => 0,
                BooleanKind::Cut => 1,
                BooleanKind::Common => 2,
            };
            let mut builder = ffi::new_boolean_builder(kind_code);
            for argument in arguments.iter() {
                builder.pin_mut().add_argument(region(regions, *argument)?.shape())?;
            }
            for tool in tools.iter() {
                builder.pin_mut().add_tool(region(regions, *tool)?.shape())?;
            }
            Ok(Region {
                shape: builder.pin_mut().finish()?,
            })
        }
        Sketch::Transform { target, op } => {
            let shape = region(regions, *target)?.shape();
            Ok(Region {
                shape: apply_transform(shape, op)?,
            })
        }
    }
}

fn region(regions: &[Region], index: SketchIndex) -> Result<&Region> {
    regions.get(index.value()).ok_or(crate::error::Error::InvalidReference {
        index: index.value(),
        current: regions.len(),
    })
}

fn from_wire(wire: UniquePtr<ffi::Shape>) -> Result<Region> {
    Ok(Region {
        shape: ffi::make_face(&wire)?,
    })
}

fn apply_transform(shape: &ffi::Shape, op: &TransformOp2) -> Result<UniquePtr<ffi::Shape>> {
    match op {
        TransformOp2::Translate { value } => {
            Ok(ffi::translate(shape, value.x.value(), value.y.value(), 0.0)?)
        }
        TransformOp2::Rotate { center, angle } => Ok(ffi::rotate(
            shape,
            center.x.value(),
            center.y.value(),
            0.0,
            0.0,
            0.0,
            1.0,
            angle.value(),
        )?),
        TransformOp2::Mirror { center, normal } => Ok(ffi::mirror(
            shape,
            center.x.value(),
            center.y.value(),
            0.0,
            normal.x.value(),
            normal.y.value(),
            0.0,
        )?),
        TransformOp2::Scale { value } => Ok(ffi::scale(
            shape,
            value.x.value(),
            value.y.value(),
            1.0,
        )?),
    }
}
