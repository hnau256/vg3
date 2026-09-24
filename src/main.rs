use std::path::Path;
use std::process::ExitCode;

use vg3::export::{ExportOptions, Format};

fn main() -> ExitCode {
    let arguments: Vec<String> = std::env::args().skip(1).collect();
    let (input, output, options) = match parse_arguments(&arguments) {
        Some(parsed) => parsed,
        None => {
            eprintln!(
                "usage: vg3 <input.json> <output.stl|output.png> \
                 [--tolerance <value>] [--png-size <px>] [--png-az <deg>] [--png-el <deg>]"
            );
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
        match arguments[index].as_str() {
            "--tolerance" => {
                index += 1;
                options.tolerance = arguments.get(index)?.parse().ok()?;
            }
            "--png-size" => {
                index += 1;
                options.image.size = arguments.get(index)?.parse().ok()?;
            }
            "--png-az" => {
                index += 1;
                options.image.azimuth = arguments.get(index)?.parse().ok()?;
            }
            "--png-el" => {
                index += 1;
                options.image.elevation = arguments.get(index)?.parse().ok()?;
            }
            argument => positional.push(argument.to_string()),
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
    vg3::export::export(&parts, format_for(output), Path::new(output), options)?;
    Ok(())
}

fn format_for(path: &str) -> Format {
    match Path::new(path).extension().and_then(|extension| extension.to_str()) {
        Some(extension) if extension.eq_ignore_ascii_case("png") => Format::Png,
        _ => Format::Stl,
    }
}
