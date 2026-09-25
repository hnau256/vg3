use std::error::Error;
use std::process::ExitCode;

use run::RunConfig;
use vg3_cache::{Cache, Disk, Memory};
use vg3_engine::{BrepCodec, ExportConfig};

mod run;

fn main() -> ExitCode {
    let arguments: Vec<String> = std::env::args().skip(1).collect();
    if !(2..=3).contains(&arguments.len()) {
        eprintln!("usage: vg3 <model.json> <export.json> [run.json]");
        return ExitCode::from(2);
    }
    match execute(
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

fn execute(
    model_path: &str,
    export_path: &str,
    run_path: Option<&str>,
) -> Result<(), Box<dyn Error>> {
    let model = vg3_model::parse(&std::fs::read_to_string(model_path)?)?;

    // Cache: memory, backed by disk at the directory the run config resolves to.
    let run_config = match run_path {
        Some(path) => RunConfig::from_json(&std::fs::read_to_string(path)?)?,
        None => RunConfig::default(),
    };
    let parts = match run_config.cache_dir() {
        Some(directory) => vg3_engine::evaluate(
            &model,
            &mut Disk::new(directory, BrepCodec).wrap_with(Memory::default()),
        )?,
        None => vg3_engine::evaluate(&model, &mut Memory::default())?,
    };

    let config = ExportConfig::from_json(&std::fs::read_to_string(export_path)?)?;
    config.export(&parts)?;
    Ok(())
}
