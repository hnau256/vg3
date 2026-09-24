use std::path::Path;
use std::process::ExitCode;

use vg3::export::Format;

fn main() -> ExitCode {
    let arguments: Vec<String> = std::env::args().skip(1).collect();
    if arguments.len() != 2 {
        eprintln!("usage: vg3 <input.json> <output.stl>");
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

fn run(input: &str, output: &str) -> vg3::Result<()> {
    let source = std::fs::read_to_string(input)?;
    let model = vg3::model::parse(&source)?;
    let parts = vg3::engine::evaluate(&model)?;
    vg3::export::export(&parts, Format::Stl, Path::new(output))?;
    Ok(())
}
