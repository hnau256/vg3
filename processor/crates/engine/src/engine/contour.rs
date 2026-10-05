use std::pin::Pin;

use cxx::UniquePtr;

use vg3_model::{Curve2, Curve3, NonEmpty, Path, Vec2, Vec3};

use crate::error::Result;
use crate::sys::ffi;

/// Builds a closed 2D contour wire from a start point and edges (auto-closing).
pub(super) fn build_contour_wire(start: Vec2, edges: &NonEmpty<Curve2>) -> Result<UniquePtr<ffi::Shape>> {
    let mut builder = ffi::new_wire_builder();
    builder.pin_mut().start(start.x.value(), start.y.value(), 0.0)?;
    for edge in edges.iter() {
        add_curve2(builder.pin_mut(), edge)?;
    }
    Ok(builder.pin_mut().finish(true)?)
}

fn add_curve2(builder: Pin<&mut ffi::WireBuilder>, edge: &Curve2) -> Result<()> {
    match edge {
        Curve2::Line { to } => builder.line(to.x.value(), to.y.value(), 0.0)?,
        Curve2::Arc { via, to } => builder.arc(
            via.x.value(),
            via.y.value(),
            0.0,
            to.x.value(),
            to.y.value(),
            0.0,
        )?,
        Curve2::Spline { points } => builder.spline(&flatten2(points.as_slice()))?,
    }
    Ok(())
}

fn flatten2(points: &[Vec2]) -> Vec<f64> {
    let mut flat = Vec::with_capacity(points.len() * 3);
    for point in points {
        flat.push(point.x.value());
        flat.push(point.y.value());
        flat.push(0.0);
    }
    flat
}

/// Builds a 3D path wire (`closed` is used for loft sections).
pub(super) fn build_path_wire(path: &Path, closed: bool) -> Result<UniquePtr<ffi::Shape>> {
    let mut builder = ffi::new_wire_builder();
    builder.pin_mut().start(
        path.start.x.value(),
        path.start.y.value(),
        path.start.z.value(),
    )?;
    for edge in path.edges.iter() {
        add_curve3(builder.pin_mut(), edge)?;
    }
    Ok(builder.pin_mut().finish(closed)?)
}

fn add_curve3(builder: Pin<&mut ffi::WireBuilder>, edge: &Curve3) -> Result<()> {
    match edge {
        Curve3::Line { to } => builder.line(to.x.value(), to.y.value(), to.z.value())?,
        Curve3::Arc { via, to } => builder.arc(
            via.x.value(),
            via.y.value(),
            via.z.value(),
            to.x.value(),
            to.y.value(),
            to.z.value(),
        )?,
        Curve3::Spline { points } => builder.spline(&flatten3(points.as_slice()))?,
        Curve3::Helix {
            pitch,
            height,
            right_handed,
        } => builder.helix(pitch.value(), height.value(), *right_handed)?,
    }
    Ok(())
}

fn flatten3(points: &[Vec3]) -> Vec<f64> {
    let mut flat = Vec::with_capacity(points.len() * 3);
    for point in points {
        flat.push(point.x.value());
        flat.push(point.y.value());
        flat.push(point.z.value());
    }
    flat
}
