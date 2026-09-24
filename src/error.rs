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

    #[error("operation result is not composed solely of solids")]
    NotASolid,

    #[error("contour has no edges")]
    EmptyContour,

    #[error("loft requires at least two sections")]
    LoftNeedsTwoSections,

    #[error("expression error: {0}")]
    Expression(String),

    #[error("opencascade error: {0}")]
    Native(#[from] cxx::Exception),

    #[error("export failed: {0}")]
    Export(String),

    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
}

pub type Result<T> = std::result::Result<T, Error>;
