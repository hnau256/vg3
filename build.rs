use std::path::PathBuf;

const OCCT_TOOLKITS: &[&str] = &[
    "TKernel",
    "TKMath",
    "TKG2d",
    "TKG3d",
    "TKGeomBase",
    "TKBRep",
    "TKGeomAlgo",
    "TKTopAlgo",
    "TKPrim",
    "TKMesh",
    "TKDESTL",
    "TKBO",
];

fn resolve_occt_directory() -> PathBuf {
    if let Ok(directory) = std::env::var("OCCT_DIR") {
        return PathBuf::from(directory);
    }
    const CANDIDATES: &[&str] = &[
        "/opt/homebrew/opt/opencascade",
        "/usr/local/opt/opencascade",
    ];
    CANDIDATES
        .iter()
        .map(PathBuf::from)
        .find(|directory| directory.join("include/opencascade").exists())
        .unwrap_or_else(|| {
            panic!("OpenCASCADE not found. Set the OCCT_DIR environment variable.")
        })
}

fn main() {
    println!("cargo:rerun-if-env-changed=OCCT_DIR");
    println!("cargo:rerun-if-changed=src/sys.rs");
    println!("cargo:rerun-if-changed=native/occt.h");
    println!("cargo:rerun-if-changed=native/occt.cpp");

    let occt_directory = resolve_occt_directory();
    let occt_include_directory = occt_directory.join("include/opencascade");
    let occt_library_directory = occt_directory.join("lib");

    println!(
        "cargo:rustc-link-search=native={}",
        occt_library_directory.display()
    );
    for toolkit in OCCT_TOOLKITS {
        println!("cargo:rustc-link-lib=dylib={toolkit}");
    }

    cxx_build::bridge("src/sys.rs")
        .file("native/occt.cpp")
        .include("native")
        .include(&occt_include_directory)
        .flag_if_supported("-std=c++17")
        .flag_if_supported("-Wno-deprecated-declarations")
        .compile("vg3-native");
}
