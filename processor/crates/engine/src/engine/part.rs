use std::rc::Rc;

use cxx::UniquePtr;

use crate::error::{Error, Result};
use crate::sys::ffi;

/// A built shape (a compound of solids). Cheap to clone — operands and cache hits share it.
#[derive(Clone)]
pub struct Part(Rc<PartShape>);

struct PartShape {
    shape: UniquePtr<ffi::Shape>,
}

impl Part {
    /// Wraps an already-built shape (used by the cache to revive a stored part).
    pub(crate) fn from_shape(shape: UniquePtr<ffi::Shape>) -> Part {
        Part(Rc::new(PartShape { shape }))
    }

    pub(crate) fn shape(&self) -> &ffi::Shape {
        self.0.shape.as_ref().expect("shape handle is never null")
    }

    pub fn solid_count(&self) -> usize {
        ffi::solid_count(self.shape())
    }

    pub fn face_count(&self) -> usize {
        ffi::face_count(self.shape())
    }

    pub fn volume(&self) -> f64 {
        ffi::volume(self.shape())
    }

    /// Total surface area of the shells.
    pub fn surface_area(&self) -> f64 {
        ffi::surface_area(self.shape())
    }

    /// Total number of edges across all solids.
    pub fn edge_count(&self) -> usize {
        ffi::edge_count(self.shape())
    }

    pub fn bounding_box(&self) -> [f64; 6] {
        let values = ffi::bounding_box(self.shape());
        let mut bounds = [0.0; 6];
        bounds.copy_from_slice(&values[..6]);
        bounds
    }

    /// Whether two parts share the same underlying shape (cache reuse).
    #[cfg(test)]
    pub(crate) fn shares_storage(&self, other: &Part) -> bool {
        Rc::ptr_eq(&self.0, &other.0)
    }
}

/// Validates a freshly built shape, normalizes it (`unify`) and wraps it in a `Part`.
pub(super) fn make_part(shape: UniquePtr<ffi::Shape>) -> Result<Part> {
    if !ffi::is_solids_only(&shape) {
        return Err(Error::NotASolid);
    }
    let unified = ffi::unify(&shape)?;
    if !ffi::is_solids_only(&unified) {
        return Err(Error::NotASolid);
    }
    Ok(Part(Rc::new(PartShape { shape: unified })))
}

/// `Part` <-> bytes, as OpenCASCADE BREP — the engine's own codec for the disk cache.
pub struct BrepCodec;

impl vg3_cache::Codec<Part> for BrepCodec {
    fn encode(&self, part: &Part) -> vg3_cache::Result<Vec<u8>> {
        ffi::brep_encode(part.shape()).map_err(|error| vg3_cache::Error::Message(error.to_string()))
    }

    fn decode(&self, bytes: &[u8]) -> vg3_cache::Result<Part> {
        ffi::brep_decode(bytes)
            .map(Part::from_shape)
            .map_err(|error| vg3_cache::Error::Message(error.to_string()))
    }
}
