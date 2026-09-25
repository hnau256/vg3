//! Cache keys — a generic, domain-agnostic fingerprint.
//!
//! `Key` is just a strong digest. The version mixed into every fingerprint (tool version plus the
//! OpenCASCADE version) makes sure cached results are only reused under identical semantics. How a
//! key is derived from a domain tree (the Merkle walk) lives with that domain, not here.

use std::hash::{Hash, Hasher};
use std::sync::OnceLock;

use crate::sys::ffi;

/// A strong (BLAKE3) fingerprint; used as a cache key and as the on-disk file name.
#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug)]
pub struct Key([u8; 32]);

impl AsRef<[u8]> for Key {
    fn as_ref(&self) -> &[u8] {
        &self.0
    }
}

/// Fingerprints any hashable value with the version-seeded BLAKE3.
pub fn fingerprint<T: Hash + ?Sized>(value: &T) -> Key {
    let mut hasher = Blake3Hasher(blake3::Hasher::new());
    hasher.0.update(version_seed().as_bytes());
    value.hash(&mut hasher);
    Key(*hasher.0.finalize().as_bytes())
}

/// Version mixed into every key, so cached entries are only reused under identical semantics.
fn version_seed() -> &'static str {
    static SEED: OnceLock<String> = OnceLock::new();
    SEED.get_or_init(|| {
        format!(
            "vg3-ir1; vg3 {}; occt {}",
            env!("CARGO_PKG_VERSION"),
            ffi::occt_version()
        )
    })
}

/// Adapter letting `std::hash::Hash` feed BLAKE3 (BLAKE3 has no `Hasher` impl of its own).
struct Blake3Hasher(blake3::Hasher);

impl Hasher for Blake3Hasher {
    fn finish(&self) -> u64 {
        let digest = self.0.finalize();
        u64::from_le_bytes(
            digest.as_bytes()[..8]
                .try_into()
                .expect("digest is 32 bytes"),
        )
    }

    fn write(&mut self, bytes: &[u8]) {
        self.0.update(bytes);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fingerprints_are_deterministic_and_sensitive() {
        assert_eq!(fingerprint("gate"), fingerprint("gate"));
        assert_ne!(fingerprint("gate"), fingerprint("door"));
        assert_ne!(fingerprint(&[1u8, 2, 3]), fingerprint(&[1u8, 2, 4]));
    }
}
