use std::path::Path;

use crate::engine::Part;
use crate::error::{Error, Result};
use crate::sys::ffi;

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Format {
    Stl,
}

pub fn export(parts: &[Part], format: Format, path: &Path) -> Result<()> {
    match format {
        Format::Stl => export_stl(parts, path),
    }
}

fn export_stl(parts: &[Part], path: &Path) -> Result<()> {
    if parts.len() != 1 {
        return Err(Error::NotImplemented("export of multiple roots"));
    }
    let path = path
        .to_str()
        .ok_or_else(|| Error::Export("output path is not valid utf-8".to_string()))?;
    if ffi::write_stl(parts[0].shape(), path)? {
        Ok(())
    } else {
        Err(Error::Export("StlAPI_Writer reported failure".to_string()))
    }
}
