//! Writes the canonical IR JSON Schema to disk (default: `<repo>/scheme/vg3.schema.json`).

use std::path::PathBuf;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let path = match std::env::args().nth(1) {
        Some(argument) => PathBuf::from(argument),
        None => vg3_schema::output_path(),
    };
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    std::fs::write(&path, vg3_schema::render())?;
    eprintln!("wrote {}", path.display());
    Ok(())
}
