//! Run configuration (a third JSON): how the CLI runs — presently where the disk cache lives.

use std::path::PathBuf;

use serde::Deserialize;

use crate::error::Result;

#[derive(Clone, Debug, Default, Deserialize)]
#[serde(deny_unknown_fields, default)]
pub struct RunConfig {
    pub cache: CacheConfig,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(deny_unknown_fields, default)]
pub struct CacheConfig {
    /// Turn the disk layer off entirely.
    pub enabled: bool,
    /// Where BREP files live; `None` means the OS default (`dirs::cache_dir()/vg3`).
    pub dir: Option<PathBuf>,
}

impl Default for CacheConfig {
    fn default() -> Self {
        CacheConfig {
            enabled: true,
            dir: None,
        }
    }
}

impl RunConfig {
    pub fn from_json(source: &str) -> Result<Self> {
        Ok(serde_json::from_str(source)?)
    }

    /// Effective disk cache directory, or `None` when disk caching is off.
    ///
    /// Precedence: `cache.dir` from the config, else `VG3_CACHE_DIR`, else the OS cache directory.
    pub fn cache_dir(&self) -> Option<PathBuf> {
        if !self.cache.enabled {
            return None;
        }
        self.cache
            .dir
            .clone()
            .or_else(|| std::env::var_os("VG3_CACHE_DIR").map(PathBuf::from))
            .or_else(default_cache_dir)
    }
}

/// Idiomatic per-user cache directory: `~/Library/Caches/vg3` (macOS), `~/.cache/vg3` (Linux),
/// `%LOCALAPPDATA%\vg3` (Windows) — never `~` itself.
fn default_cache_dir() -> Option<PathBuf> {
    dirs::cache_dir().map(|base| base.join("vg3"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn defaults_to_enabled_with_the_os_directory() {
        let config = RunConfig::default();
        assert!(config.cache.enabled);
        let directory = config.cache_dir().expect("a default directory exists");
        assert!(directory.ends_with("vg3"));
        assert_ne!(directory, dirs::home_dir().unwrap());
    }

    #[test]
    fn explicit_directory_wins() {
        let config =
            RunConfig::from_json(r#"{ "cache": { "dir": "/tmp/vg3-explicit" } }"#).expect("parses");
        assert_eq!(config.cache_dir(), Some(PathBuf::from("/tmp/vg3-explicit")));
    }

    #[test]
    fn can_be_disabled() {
        let config = RunConfig::from_json(r#"{ "cache": { "enabled": false } }"#).expect("parses");
        assert_eq!(config.cache_dir(), None);
    }

    #[test]
    fn unknown_fields_are_rejected() {
        assert!(RunConfig::from_json(r#"{ "nonsense": 1 }"#).is_err());
    }
}
