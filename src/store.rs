//! On-disk cache layer: one BREP file per key, under `$VG3_CACHE_DIR`.

use std::path::PathBuf;

use crate::cache::Cache;
use crate::engine::Part;
use crate::error::{Error, Result};
use crate::key::Key;
use crate::sys::ffi;

/// Disk layer of the cache (`Cache<Key, Part>`). Opt-in: without `VG3_CACHE_DIR` there is none.
pub struct Disk {
    directory: PathBuf,
}

impl Disk {
    /// Opens the cache directory from `VG3_CACHE_DIR` (so nothing is written behind the user's back).
    pub fn from_env() -> Option<Disk> {
        std::env::var_os("VG3_CACHE_DIR")
            .map(PathBuf::from)
            .map(|directory| Disk { directory })
    }

    fn path(&self, key: Key) -> PathBuf {
        self.directory.join(format!("{}.brep", key.to_hex()))
    }

    fn write(&self, key: Key, part: &Part) -> Result<()> {
        std::fs::create_dir_all(&self.directory)?;
        let path = self.path(key);
        let temporary = path.with_extension("brep.tmp");
        let file = temporary
            .to_str()
            .ok_or_else(|| Error::Cache("cache path is not valid utf-8".to_string()))?;
        if !ffi::write_brep(part.shape(), file)? {
            return Err(Error::Cache(
                "BRepTools::Write reported failure".to_string(),
            ));
        }
        std::fs::rename(&temporary, &path)?;
        Ok(())
    }
}

impl Cache<Key, Part> for Disk {
    /// A miss — or a corrupt entry, which is dropped — yields `None`: the cache is an optimization
    /// and must never fail a build.
    fn get(&mut self, key: &Key) -> Option<Part> {
        let path = self.path(*key);
        let file = path.to_str()?;
        if !path.is_file() {
            return None;
        }
        match ffi::read_brep(file) {
            Ok(shape) => Some(Part::from_shape(shape)),
            Err(_) => {
                let _ = std::fs::remove_file(&path);
                None
            }
        }
    }

    /// Best-effort: a write failure is warned about, never fatal.
    fn put(&mut self, key: &Key, part: &Part) {
        if let Err(error) = self.write(*key, part) {
            eprintln!("vg3: disk cache write skipped: {error}");
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::key;
    use crate::model::{Model, Node, Scalar};

    fn scalar(value: f64) -> Scalar {
        Scalar::try_from(value).expect("finite")
    }

    fn box_node() -> Node<usize> {
        Node::Box {
            width: scalar(1.0),
            length: scalar(1.0),
            height: scalar(1.0),
        }
    }

    fn box_key() -> Key {
        key::node_key(&Node::Box {
            width: scalar(1.0),
            length: scalar(1.0),
            height: scalar(1.0),
        })
    }

    fn clear() -> Disk {
        let directory = std::env::temp_dir().join("vg3-disk-test");
        let _ = std::fs::remove_dir_all(&directory);
        Disk { directory }
    }

    #[test]
    fn put_and_get_round_trip() {
        let mut disk = clear();
        let part = crate::engine::evaluate(
            &Model {
                version: 1,
                parts: vec![box_node()],
            },
            &mut crate::cache::Noop,
        )
        .expect("builds")
        .pop()
        .expect("one root");

        let key = box_key();
        disk.put(&key, &part);
        assert!(disk.get(&key).is_some(), "stored entry must load back");
        let _ = std::fs::remove_dir_all(&disk.directory);
    }

    #[test]
    fn missing_entry_is_a_miss() {
        let mut disk = clear();
        assert!(disk.get(&box_key()).is_none());
    }
}
