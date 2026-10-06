use std::collections::HashSet;
use std::path::PathBuf;

use cxx::UniquePtr;
use serde::Deserialize;

use crate::engine::Output;
use crate::error::{Error, Result};
use crate::render::{self, RenderOptions};
use crate::sys::ffi;
use vg3_model::Scalar;

/// Triangulation tolerance shared by the exporters that tessellate (STL, PNG).
const DEFAULT_TOLERANCE: f64 = 0.1;

/// How an exporter lays its results out on disk. Formats that can either emit one combined file or
/// one file per part (STL, PNG) embed it as their `output` field; a format that is always a single
/// file (a future STEP) simply would not have the field.
#[derive(Clone, Debug, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case", deny_unknown_fields)]
pub enum ExportLayout {
    /// Everything into one file.
    Single { filename: PathBuf },
    /// One file per exported part — `<path>/<name>.<extension>` — named by the model's export list.
    /// The directory is created if it does not exist.
    Multi { path: PathBuf },
}

impl ExportLayout {
    /// Splits the outputs into target files: one batch for `single`, one per part for `multi`.
    fn batches<'a>(
        &self,
        outputs: &'a [Output],
        extension: &str,
    ) -> Result<Vec<(PathBuf, &'a [Output])>> {
        match self {
            ExportLayout::Single { filename } => Ok(vec![(filename.clone(), outputs)]),
            ExportLayout::Multi { path } => {
                std::fs::create_dir_all(path)?;
                let mut names = HashSet::with_capacity(outputs.len());
                let mut batches = Vec::with_capacity(outputs.len());
                for output in outputs {
                    if output.name.is_empty() {
                        return Err(Error::Export(
                            "multi output needs a non-empty name for every part".to_string(),
                        ));
                    }
                    if !names.insert(output.name.as_str()) {
                        return Err(Error::Export(format!(
                            "multi output has a duplicate part name {:?}",
                            output.name
                        )));
                    }
                    let file = path.join(format!("{}.{extension}", output.name));
                    batches.push((file, std::slice::from_ref(output)));
                }
                Ok(batches)
            }
        }
    }
}

fn default_tolerance() -> Scalar {
    Scalar::try_from(DEFAULT_TOLERANCE).expect("the default tolerance is finite")
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
#[serde(tag = "format", rename_all = "snake_case", deny_unknown_fields)]
pub enum ExportConfig {
    Stl {
        output: ExportLayout,
        #[serde(default = "default_tolerance")]
        tolerance: Scalar,
    },
    Png {
        output: ExportLayout,
        #[serde(default = "default_tolerance")]
        tolerance: Scalar,
        #[serde(default = "default_size")]
        size: u32,
        #[serde(default = "default_azimuth")]
        azimuth: Scalar,
        #[serde(default = "default_elevation")]
        elevation: Scalar,
    },
    /// STEP is always a single file (no `output` layout).
    Step {
        filename: PathBuf,
    },
}

impl ExportConfig {
    pub fn from_json(source: &str) -> serde_json::Result<Self> {
        serde_json::from_str(source)
    }

    pub fn export(&self, outputs: &[Output]) -> Result<()> {
        if outputs.is_empty() {
            return Ok(());
        }
        match self {
            ExportConfig::Stl { output, tolerance } => {
                export_stl(outputs, output, tolerance.value())
            }
            ExportConfig::Png {
                output,
                tolerance,
                size,
                azimuth,
                elevation,
            } => export_png(
                outputs,
                output,
                tolerance.value(),
                &RenderOptions {
                    size: *size,
                    azimuth: azimuth.value(),
                    elevation: elevation.value(),
                },
            ),
            ExportConfig::Step { filename } => export_step(outputs, filename),
        }
    }
}

fn export_stl(outputs: &[Output], layout: &ExportLayout, tolerance: f64) -> Result<()> {
    for (path, batch) in layout.batches(outputs, "stl")? {
        let compound = build_compound(batch)?;
        let path = path
            .to_str()
            .ok_or_else(|| Error::Export("output path is not valid utf-8".to_string()))?;
        ffi::write_stl(&compound, path, tolerance)?;
    }
    Ok(())
}

fn export_step(outputs: &[Output], filename: &std::path::Path) -> Result<()> {
    let path = filename
        .to_str()
        .ok_or_else(|| Error::Export("output path is not valid utf-8".to_string()))?;
    let mut builder = ffi::new_step_builder();
    for output in outputs {
        let (has_color, r, g, b) = match output.color {
            Some(color) => {
                let [r, g, b] = color.components();
                (true, r, g, b)
            }
            None => (false, 0.0, 0.0, 0.0),
        };
        builder
            .pin_mut()
            .add_part(output.part.shape(), &output.name, has_color, r, g, b)?;
    }
    builder.pin_mut().write(path)?;
    Ok(())
}

fn export_png(
    outputs: &[Output],
    layout: &ExportLayout,
    tolerance: f64,
    options: &RenderOptions,
) -> Result<()> {
    for (path, batch) in layout.batches(outputs, "png")? {
        let mut items = Vec::with_capacity(batch.len());
        for output in batch {
            items.push(render::Item {
                color: output.color.map(|color| color.components()),
                triangles: ffi::triangulation(output.part.shape(), tolerance)?,
            });
        }
        render::render_png(&items, &path, options)?;
    }
    Ok(())
}

fn build_compound(outputs: &[Output]) -> Result<UniquePtr<ffi::Shape>> {
    let mut builder = ffi::new_compound_builder();
    for output in outputs {
        builder.pin_mut().push(output.part.shape())?;
    }
    Ok(builder.pin_mut().finish()?)
}
