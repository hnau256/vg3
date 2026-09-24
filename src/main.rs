use std::path::Path;
use std::process::ExitCode;

use vg3::export::{ExportOptions, Format};

fn main() -> ExitCode {
    let arguments: Vec<String> = std::env::args().skip(1).collect();
    let (input, output, options) = match parse_arguments(&arguments) {
        Some(parsed) => parsed,
        None => {
            eprintln!("usage: vg3 <input.json> <output.stl> [--tolerance <value>]");
            return ExitCode::from(2);
        }
    };
    match run(&input, &output, &options) {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => {
            eprintln!("vg3: {error}");
            ExitCode::FAILURE
        }
    }
}

fn parse_arguments(arguments: &[String]) -> Option<(String, String, ExportOptions)> {
    let mut positional = Vec::new();
    let mut options = ExportOptions::default();
    let mut index = 0;
    while index < arguments.len() {
        let argument = &arguments[index];
        if argument == "--tolerance" {
            index += 1;
            let value = arguments.get(index)?.parse::<f64>().ok()?;
            options.tolerance = value;
        } else {
            positional.push(argument.clone());
        }
        index += 1;
    }
    if positional.len() != 2 {
        return None;
    }
    Some((positional[0].clone(), positional[1].clone(), options))
}

fn run(input: &str, output: &str, options: &ExportOptions) -> vg3::Result<()> {
    let source = std::fs::read_to_string(input)?;
    let model = vg3::model::parse(&source)?;
    let parts = vg3::engine::evaluate(&model)?;
    vg3::export::export(&parts, Format::Stl, Path::new(output), options)?;
    Ok(())
}
