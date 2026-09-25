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
}

pub type Result<T> = std::result::Result<T, Error>;
