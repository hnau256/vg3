use std::process::ExitCode;

use vg3::cache::{Cache, Memory};
use vg3::export::ExportConfig;
use vg3::run::RunConfig;
use vg3::store::Disk;

fn main() -> ExitCode {
    let arguments: Vec<String> = std::env::args().skip(1).collect();
    if !(2..=3).contains(&arguments.len()) {
        eprintln!("usage: vg3 <model.json> <export.json> [run.json]");
        return ExitCode::from(2);
    }
    match run(
        &arguments[0],
        &arguments[1],
        arguments.get(2).map(String::as_str),
    ) {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => {
            eprintln!("vg3: {error}");
            ExitCode::FAILURE
        }
    }
}

fn run(model_path: &str, export_path: &str, run_path: Option<&str>) -> vg3::Result<()> {
    let model_source = std::fs::read_to_string(model_path)?;
    let model = vg3::model::parse(&model_source)?;

    // Cache: memory, backed by disk at the directory the run config resolves to.
    let run_config = match run_path {
        Some(path) => RunConfig::from_json(&std::fs::read_to_string(path)?)?,
        None => RunConfig::default(),
    };
    let parts = match run_config.cache_dir() {
        Some(directory) => vg3::engine::evaluate(&model, move |codec| {
            Disk::new(directory, codec).wrap_with(Memory::default())
        })?,
        None => vg3::engine::evaluate(&model, |_codec| Memory::default())?,
    };

    let export_source = std::fs::read_to_string(export_path)?;
    let config = ExportConfig::from_json(&export_source)?;
    config.export(&parts)
}
