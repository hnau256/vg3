use std::path::Path;

use cxx::UniquePtr;

use crate::engine::Part;
use crate::error::{Error, Result};
use crate::render::{self, RenderOptions};
use crate::sys::ffi;

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Format {
    Stl,
    Png,
}

#[derive(Clone, Copy, Debug)]
pub struct ExportOptions {
    pub tolerance: f64,
    pub image: RenderOptions,
}

impl Default for ExportOptions {
    fn default() -> Self {
        ExportOptions {
            tolerance: 0.1,
            image: RenderOptions::default(),
        }
    }
}

pub fn export(parts: &[Part], format: Format, path: &Path, options: &ExportOptions) -> Result<()> {
    match format {
        Format::Stl => export_stl(parts, path, options.tolerance),
        Format::Png => export_png(parts, path, options),
    }
}

fn export_stl(parts: &[Part], path: &Path, tolerance: f64) -> Result<()> {
    if parts.is_empty() {
        return Ok(());
    }
    let compound = build_compound(parts)?;
    let path = path
        .to_str()
        .ok_or_else(|| Error::Export("output path is not valid utf-8".to_string()))?;
    if ffi::write_stl(&compound, path, tolerance)? {
        Ok(())
    } else {
        Err(Error::Export("StlAPI_Writer reported failure".to_string()))
    }
}

fn export_png(parts: &[Part], path: &Path, options: &ExportOptions) -> Result<()> {
    let compound = build_compound(parts)?;
    let triangles = ffi::triangulation(&compound, options.tolerance)?;
    render::render_png(&triangles, path, &options.image)
}

fn build_compound(parts: &[Part]) -> Result<UniquePtr<ffi::Shape>> {
    let mut builder = ffi::new_compound_builder();
    for part in parts {
        builder.pin_mut().push(part.shape())?;
    }
    Ok(builder.pin_mut().finish()?)
}
