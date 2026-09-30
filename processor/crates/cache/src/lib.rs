//! A small, domain-agnostic cache: a `Cache`/`Codec` trait pair plus in-memory, disk, no-op and
//! layered implementations. Nothing here knows about any particular model or value type.

mod cache;
mod error;
mod key;
mod store;

pub use cache::{get_or_put, Cache, Codec, Layered, Memory, Noop};
pub use error::{Error, Result};
pub use key::{Fingerprinter, Key};
pub use store::Disk;
