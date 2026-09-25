use thiserror::Error;

#[derive(Debug, Error)]
pub enum Error {
    #[error("{0}")]
    Model(#[from] vg3_model::Error),

    #[error("{0}")]
    Cache(#[from] vg3_cache::Error),

    #[error("opencascade error: {0}")]
    Native(#[from] cxx::Exception),

    #[error("operation result is not composed solely of solids")]
    NotASolid,

    #[error("contour has no edges")]
    EmptyContour,

    #[error("reference index {index} must be less than the current index {current}")]
    InvalidReference { index: usize, current: usize },

    #[error("export index {index} is out of range (parts has {parts})")]
    ExportIndex { index: usize, parts: usize },

    #[error("node requires at least one operand")]
    MissingOperand,

    #[error("loft requires at least two sections")]
    LoftNeedsTwoSections,

    #[error("expression error: {0}")]
    Expression(String),

    #[error("export failed: {0}")]
    Export(String),

    #[error("io error: {0}")]
    Io(#[from] std::io::Error),

    #[error("json error: {0}")]
    Json(#[from] serde_json::Error),
}

pub type Result<T> = std::result::Result<T, Error>;
