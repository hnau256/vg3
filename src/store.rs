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
    /// Disk layer backed by `directory`.
    pub fn at(directory: PathBuf) -> Disk {
        Disk { directory }
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
    use crate::cache::{Memory, Noop};
    use crate::key;
    use crate::model::{Model, Node, Scalar};

    fn scalar(value: f64) -> Scalar {
        Scalar::try_from(value).expect("finite")
    }

    fn box_node(size: f64) -> Node<usize> {
        Node::Box {
            width: scalar(size),
            length: scalar(size),
            height: scalar(size),
        }
    }

    fn box_key(size: f64) -> Key {
        key::node_key(&Node::Box {
            width: scalar(size),
            length: scalar(size),
            height: scalar(size),
        })
    }

    fn build_box(size: f64) -> Part {
        crate::engine::evaluate(
            &Model {
                version: 1,
                parts: vec![box_node(size)],
            },
            &mut Noop,
        )
        .expect("builds")
        .pop()
        .expect("one root")
    }

    /// Each test gets its own directory (tests run in parallel).
    fn disk(name: &str) -> Disk {
        let directory = std::env::temp_dir().join(format!("vg3-disk-test-{name}"));
        let _ = std::fs::remove_dir_all(&directory);
        Disk::at(directory)
    }

    #[test]
    fn put_and_get_round_trip() {
        let mut disk = disk("round-trip");
        let key = box_key(2.0);
        disk.put(&key, &build_box(2.0));
        let loaded = disk.get(&key).expect("hit");
        assert!(
            (loaded.volume() - 8.0).abs() < 1e-9,
            "geometry must survive the round trip"
        );
        let _ = std::fs::remove_dir_all(&disk.directory);
    }

    #[test]
    fn missing_entry_is_a_miss() {
        let mut disk = disk("missing");
        assert!(disk.get(&box_key(2.0)).is_none());
    }

    #[test]
    fn corrupt_entry_is_dropped() {
        let mut disk = disk("corrupt");
        let key = box_key(2.0);
        std::fs::create_dir_all(&disk.directory).expect("create dir");
        let path = disk.path(key);
        std::fs::write(&path, b"not a BREP file").expect("write garbage");
        assert!(disk.get(&key).is_none(), "corrupt entry must miss");
        assert!(!path.exists(), "corrupt entry must be dropped");
        let _ = std::fs::remove_dir_all(&disk.directory);
    }

    #[test]
    fn engine_reads_from_disk() {
        let mut disk = disk("engine-reads");
        let directory = disk.directory.clone();
        // Poison the key of a 2x2x2 box with a 1x1x1 box: if the engine consults the disk, it must
        // return the small box, proving the layering actually reads disk-backed entries.
        disk.put(&box_key(2.0), &build_box(1.0));
        let model = Model {
            version: 1,
            parts: vec![box_node(2.0)],
        };
        let parts = crate::engine::evaluate(&model, &mut disk.wrap_with(Memory::default()))
            .expect("builds");
        assert!(
            (parts[0].volume() - 1.0).abs() < 1e-9,
            "engine must have read the disk entry"
        );
        let _ = std::fs::remove_dir_all(&directory);
    }
}
