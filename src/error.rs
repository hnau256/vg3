use thiserror::Error;

#[derive(Debug, Error)]
pub enum Error {
    #[error("json error: {0}")]
    Json(#[from] serde_json::Error),

    #[error("unsupported ir version: {0}")]
    UnsupportedVersion(u32),

    #[error("scalar must be finite")]
    NonFiniteScalar,

    #[error("normal vector must be non-zero")]
    ZeroNormal,

    #[error("reference index {index} must be less than the current index {current}")]
    InvalidReference { index: usize, current: usize },

    #[error("node requires at least one operand")]
    MissingOperand,

    #[error("not implemented yet: {0}")]
    NotImplemented(&'static str),

    #[error("opencascade error: {0}")]
    Native(#[from] cxx::Exception),

    #[error("export failed: {0}")]
    Export(String),

    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
}

pub type Result<T> = std::result::Result<T, Error>;
