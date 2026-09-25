//! On-disk cache layer: one file per key.
//!
//! Domain-agnostic: a [`Codec`] turns values into bytes and back (the caller supplies the one for
//! its value type), and the key just has to expose raw bytes for the file name.

use std::marker::PhantomData;
use std::path::PathBuf;

use crate::cache::{Cache, Codec};
use crate::error::Result;

/// Disk layer of the cache (`Cache<K, V>`).
pub struct Disk<K, V, C> {
    directory: PathBuf,
    codec: C,
    marker: PhantomData<fn(K) -> V>,
}

impl<K, V, C> Disk<K, V, C> {
    /// Disk layer backed by `directory`, encoding values with `codec`.
    pub fn new(directory: PathBuf, codec: C) -> Self {
        Disk {
            directory,
            codec,
            marker: PhantomData,
        }
    }
}

impl<K: AsRef<[u8]>, V, C: Codec<V>> Disk<K, V, C> {
    fn path(&self, key: &K) -> PathBuf {
        let mut name = String::with_capacity(key.as_ref().len() * 2);
        for byte in key.as_ref() {
            name.push_str(&format!("{byte:02x}"));
        }
        self.directory.join(format!("{name}.bin"))
    }

    /// Stores atomically (temporary file + rename).
    fn write(&self, key: &K, value: &V) -> Result<()> {
        std::fs::create_dir_all(&self.directory)?;
        let path = self.path(key);
        let temporary = path.with_extension("bin.tmp");
        std::fs::write(&temporary, self.codec.encode(value)?)?;
        std::fs::rename(&temporary, &path)?;
        Ok(())
    }
}

impl<K: AsRef<[u8]>, V, C: Codec<V>> Cache<K, V> for Disk<K, V, C> {
    /// A miss — or an undecodable entry, which is dropped — yields `None`: the cache is an
    /// optimization and must never fail a build.
    fn get(&mut self, key: &K) -> Option<V> {
        let path = self.path(key);
        let bytes = std::fs::read(&path).ok()?;
        match self.codec.decode(&bytes) {
            Ok(value) => Some(value),
            Err(_) => {
                let _ = std::fs::remove_file(&path);
                None
            }
        }
    }

    /// Best-effort: a write failure is warned about, never fatal.
    fn put(&mut self, key: &K, value: &V) {
        if let Err(error) = self.write(key, value) {
            eprintln!("vg3: disk cache write skipped: {error}");
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::error::Error;

    /// A trivial codec (bytes with a magic prefix) so the store tests need no domain types. The
    /// prefix lets a garbage file fail to decode.
    struct Bytes;

    const MAGIC: &[u8] = b"VG3T";

    impl Codec<Vec<u8>> for Bytes {
        fn encode(&self, value: &Vec<u8>) -> Result<Vec<u8>> {
            let mut out = MAGIC.to_vec();
            out.extend_from_slice(value);
            Ok(out)
        }

        fn decode(&self, bytes: &[u8]) -> Result<Vec<u8>> {
            let payload = bytes
                .strip_prefix(MAGIC)
                .ok_or_else(|| Error::Cache("bad magic".to_string()))?;
            Ok(payload.to_vec())
        }
    }

    type TestDisk = Disk<[u8; 4], Vec<u8>, Bytes>;

    fn disk(name: &str) -> TestDisk {
        let directory = std::env::temp_dir().join(format!("vg3-disk-test-{name}"));
        let _ = std::fs::remove_dir_all(&directory);
        Disk::new(directory, Bytes)
    }

    fn key(value: u8) -> [u8; 4] {
        [value, value, value, value]
    }

    #[test]
    fn put_and_get_round_trip() {
        let mut disk = disk("round-trip");
        disk.put(&key(1), &vec![1, 2, 3]);
        assert_eq!(disk.get(&key(1)), Some(vec![1, 2, 3]));
        let _ = std::fs::remove_dir_all(&disk.directory);
    }

    #[test]
    fn missing_entry_is_a_miss() {
        let mut disk = disk("missing");
        assert_eq!(disk.get(&key(2)), None);
    }

    #[test]
    fn undecodable_entry_is_dropped() {
        let mut disk = disk("corrupt");
        std::fs::create_dir_all(&disk.directory).expect("create dir");
        let path = disk.path(&key(3));
        std::fs::write(&path, b"garbage").expect("write");
        assert!(disk.get(&key(3)).is_none());
        assert!(!path.exists(), "undecodable entry must be dropped");
        let _ = std::fs::remove_dir_all(&disk.directory);
    }
}
