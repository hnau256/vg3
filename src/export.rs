use std::path::{Path, PathBuf};

use cxx::UniquePtr;
use serde::Deserialize;

use crate::engine::Part;
use crate::error::{Error, Result};
use crate::model::Scalar;
use crate::render::{self, RenderOptions};
use crate::sys::ffi;

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

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Format {
    Stl,
    Png,
}

fn default_tolerance() -> Scalar {
    Scalar::try_from(0.1).expect("0.1 is finite")
}

fn default_size() -> u32 {
    512
}

fn default_azimuth() -> Scalar {
    Scalar::try_from(35.0).expect("35 is finite")
}

fn default_elevation() -> Scalar {
    Scalar::try_from(25.0).expect("25 is finite")
}

#[derive(Clone, Debug, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case", deny_unknown_fields)]
pub enum ExportConfig {
    Stl {
        path: PathBuf,
        #[serde(default = "default_tolerance")]
        tolerance: Scalar,
    },
    Png {
        path: PathBuf,
        #[serde(default = "default_size")]
        size: u32,
        #[serde(default = "default_azimuth")]
        azimuth: Scalar,
        #[serde(default = "default_elevation")]
        elevation: Scalar,
    },
}

impl ExportConfig {
    pub fn from_json(source: &str) -> Result<Self> {
        Ok(serde_json::from_str(source)?)
    }

    pub fn export(&self, parts: &[Part]) -> Result<()> {
        match self {
            ExportConfig::Stl { path, tolerance } => export(
                parts,
                Format::Stl,
                path,
                &ExportOptions {
                    tolerance: tolerance.value(),
                    ..ExportOptions::default()
                },
            ),
            ExportConfig::Png {
                path,
                size,
                azimuth,
                elevation,
            } => export(
                parts,
                Format::Png,
                path,
                &ExportOptions {
                    image: RenderOptions {
                        size: *size,
                        azimuth: azimuth.value(),
                        elevation: elevation.value(),
                    },
                    ..ExportOptions::default()
                },
            ),
        }
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
