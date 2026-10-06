use std::error::Error;
use std::io::Read;
use std::process::ExitCode;

use clap::{ArgGroup, Parser};
use run::RunConfig;
use vg3_cache::{Cache, Disk, Memory};
use vg3_engine::{BrepPartCodec, BrepRegionCodec, ExportConfig};

mod run;

/// Generate solid 3D models from typed JSON IR and export them.
#[derive(Debug, Parser)]
#[command(name = "vg3", version, about)]
#[command(group(
    ArgGroup::new("export")
        .required(true)
        .args(["export_config_file", "export_config_json"])
))]
struct Args {
    /// Model file (`-` reads standard input); omitting it also reads standard input.
    #[arg(long, value_name = "PATH", conflicts_with = "model_json")]
    model_file: Option<String>,
    /// Model JSON, inline.
    #[arg(long, value_name = "JSON", conflicts_with = "model_file")]
    model_json: Option<String>,

    /// Export config file (`-` reads standard input).
    #[arg(long, value_name = "PATH", conflicts_with = "export_config_json")]
    export_config_file: Option<String>,
    /// Export config JSON, inline.
    #[arg(long, value_name = "JSON", conflicts_with = "export_config_file")]
    export_config_json: Option<String>,

    /// Run config file (`-` reads standard input).
    #[arg(long, value_name = "PATH", conflicts_with = "run_config_json")]
    run_config_file: Option<String>,
    /// Run config JSON, inline.
    #[arg(long, value_name = "JSON", conflicts_with = "run_config_file")]
    run_config_json: Option<String>,
}

fn main() -> ExitCode {
    let args = Args::parse();
    match execute(&args) {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => {
            eprintln!("vg3: {error}");
            ExitCode::FAILURE
        }
    }
}

fn execute(args: &Args) -> Result<(), Box<dyn Error>> {
    let model_source = match input(args.model_file.as_deref(), args.model_json.as_deref())? {
        Some(source) => source,
        None => read_stdin()?,
    };
    let model = vg3_model::parse(&model_source)?;

    let run_config = match input(
        args.run_config_file.as_deref(),
        args.run_config_json.as_deref(),
    )? {
        Some(source) => RunConfig::from_json(&source)?,
        None => RunConfig::default(),
    };

    // Cache: memory alone, or memory backed by disk at the directory the run config resolves to.
    // Parts and regions share the directory — their Merkle keys live in disjoint input domains.
    let outputs = match run_config.cache_dir() {
        Some(directory) => {
            let mut parts =
                Disk::new(directory.clone(), BrepPartCodec).wrap_with(Memory::default());
            let mut sketches = Disk::new(directory, BrepRegionCodec).wrap_with(Memory::default());
            vg3_engine::evaluate(&model, &mut parts, &mut sketches)?
        }
        None => {
            let mut parts = Memory::default();
            let mut sketches = Memory::default();
            vg3_engine::evaluate(&model, &mut parts, &mut sketches)?
        }
    };

    let export_source = input(
        args.export_config_file.as_deref(),
        args.export_config_json.as_deref(),
    )?
    .ok_or("an export config is required (--export-config-file or --export-config-json)")?;
    let config = ExportConfig::from_json(&export_source)?;
    config.export(&outputs)?;
    Ok(())
}

/// Resolves a `--…-file`/`--…-json` pair to its text; `None` when neither was given.
fn input(file: Option<&str>, json: Option<&str>) -> Result<Option<String>, Box<dyn Error>> {
    match (file, json) {
        (Some(path), _) => Ok(Some(read(path)?)),
        (None, Some(json)) => Ok(Some(json.to_owned())),
        (None, None) => Ok(None),
    }
}

/// Reads a file, treating `-` as standard input.
fn read(path: &str) -> Result<String, Box<dyn Error>> {
    if path == "-" {
        read_stdin()
    } else {
        Ok(std::fs::read_to_string(path)?)
    }
}

fn read_stdin() -> Result<String, Box<dyn Error>> {
    let mut buffer = String::new();
    std::io::stdin().read_to_string(&mut buffer)?;
    Ok(buffer)
}
