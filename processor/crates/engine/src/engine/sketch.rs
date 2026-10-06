//! The `Sketch -> Region` transformation: a planar face per sketch node.

use std::rc::Rc;

use cxx::UniquePtr;

use vg3_cache::{get_or_put, Cache, Key};
use vg3_model::{Sketch, SketchIndex, TransformOp2};

use crate::engine::contour::build_contour_wire;
use crate::engine::fillet2d::evaluate_fillet2d;
use crate::engine::op::{boolean_code, join_code};
use crate::error::Result;
use crate::sys::ffi;

/// A built planar region (a face). The counterpart of [`crate::engine::Part`] for sketches. Cheap to
/// clone — operands and cache hits share it.
#[derive(Clone)]
pub struct Region(Rc<RegionShape>);

struct RegionShape {
    shape: UniquePtr<ffi::Shape>,
}

impl Region {
    /// Wraps an already-built face (used by the cache to revive a stored region).
    pub(crate) fn from_shape(shape: UniquePtr<ffi::Shape>) -> Region {
        Region(Rc::new(RegionShape { shape }))
    }

    pub(super) fn shape(&self) -> &ffi::Shape {
        self.0.shape.as_ref().expect("shape handle is never null")
    }

    /// Whether two regions share the same underlying face (cache reuse).
    #[cfg(test)]
    pub(crate) fn shares_storage(&self, other: &Region) -> bool {
        Rc::ptr_eq(&self.0, &other.0)
    }
}

/// Builds every sketch in the arena, in order (references are `index < current`), through the region
/// cache. `keys[i]` is the Merkle key of `sketches[i]`.
pub(super) fn build_regions<S: Cache<Key, Region> + ?Sized>(
    sketches: &[Sketch<SketchIndex>],
    keys: &[Key],
    cache: &mut S,
) -> Result<Vec<Region>> {
    let mut regions: Vec<Region> = Vec::with_capacity(sketches.len());
    for (sketch, key) in sketches.iter().zip(keys) {
        let region = get_or_put(cache, key, |_| evaluate_sketch(sketch, &regions))?;
        regions.push(region);
    }
    Ok(regions)
}

fn evaluate_sketch(sketch: &Sketch<SketchIndex>, regions: &[Region]) -> Result<Region> {
    match sketch {
        Sketch::Circle { radius } => Ok(Region::from_shape(ffi::make_circle(radius.value())?)),
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
        Sketch::Contour { start, edges } => from_wire(build_contour_wire(*start, edges)?),
        Sketch::Bool {
            kind,
            arguments,
            tools,
        } => {
            let kind_code = boolean_code(*kind);
            let mut builder = ffi::new_boolean_builder(kind_code);
            for argument in arguments.iter() {
                builder
                    .pin_mut()
                    .add_argument(region(regions, *argument)?.shape())?;
            }
            for tool in tools.iter() {
                builder
                    .pin_mut()
                    .add_tool(region(regions, *tool)?.shape())?;
            }
            let built = builder.pin_mut().finish()?;
            Ok(Region::from_shape(ffi::as_face(&built)?))
        }
        Sketch::Transform { target, op } => {
            let shape = region(regions, *target)?.shape();
            Ok(Region::from_shape(apply_transform(shape, op)?))
        }
        Sketch::Fillet { target, radius } => Ok(Region::from_shape(evaluate_fillet2d(
            region(regions, *target)?.shape(),
            radius,
        )?)),
        Sketch::Offset {
            target,
            distance,
            join,
        } => Ok(Region::from_shape(ffi::offset2d(
            region(regions, *target)?.shape(),
            distance.value(),
            join_code(*join),
        )?)),
    }
}

fn region(regions: &[Region], index: SketchIndex) -> Result<&Region> {
    regions
        .get(index.value())
        .ok_or(crate::error::Error::InvalidReference {
            index: index.value(),
            current: regions.len(),
        })
}

fn from_wire(wire: UniquePtr<ffi::Shape>) -> Result<Region> {
    Ok(Region::from_shape(ffi::make_face(&wire)?))
}

fn apply_transform(shape: &ffi::Shape, op: &TransformOp2) -> Result<UniquePtr<ffi::Shape>> {
    match op {
        TransformOp2::Translate { value } => Ok(ffi::translate(
            shape,
            value.x.value(),
            value.y.value(),
            0.0,
        )?),
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
        TransformOp2::Scale { value } => {
            Ok(ffi::scale(shape, value.x.value(), value.y.value(), 1.0)?)
        }
    }
}

/// `Region` <-> bytes, as OpenCASCADE BREP — the engine's own codec for the sketch region cache.
pub struct BrepRegionCodec;

impl vg3_cache::Codec<Region> for BrepRegionCodec {
    fn encode(&self, region: &Region) -> vg3_cache::Result<Vec<u8>> {
        ffi::brep_encode(region.shape())
            .map_err(|error| vg3_cache::Error::Message(error.to_string()))
    }

    fn decode(&self, bytes: &[u8]) -> vg3_cache::Result<Region> {
        ffi::brep_decode(bytes)
            .map(Region::from_shape)
            .map_err(|error| vg3_cache::Error::Message(error.to_string()))
    }
}
