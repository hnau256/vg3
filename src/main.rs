use std::process::ExitCode;

use vg3::export::ExportConfig;

fn main() -> ExitCode {
    let arguments: Vec<String> = std::env::args().skip(1).collect();
    if arguments.len() != 2 {
        eprintln!("usage: vg3 <model.json> <export.json>");
        return ExitCode::from(2);
    }
    match run(&arguments[0], &arguments[1]) {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => {
            eprintln!("vg3: {error}");
            ExitCode::FAILURE
        }
    }
}

fn run(model_path: &str, export_path: &str) -> vg3::Result<()> {
    let model_source = std::fs::read_to_string(model_path)?;
    let model = vg3::model::parse(&model_source)?;
    let parts = vg3::engine::evaluate(&model)?;
    let export_source = std::fs::read_to_string(export_path)?;
    let config = ExportConfig::from_json(&export_source)?;
    config.export(&parts)
}
