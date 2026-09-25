//! Cache keys — a generic, caller-seeded fingerprint.
//!
//! `Key` is just a strong digest. Fingerprinting is seeded by the caller, so a domain tool can mix
//! its own version (and that of its backend) into every key, ensuring entries are only reused
//! under identical semantics. How a key is derived from a domain tree is not this crate's concern.

use std::hash::{Hash, Hasher};

/// A strong (BLAKE3) fingerprint; used as a cache key and as an on-disk file name.
#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug)]
pub struct Key([u8; 32]);

impl AsRef<[u8]> for Key {
    fn as_ref(&self) -> &[u8] {
        &self.0
    }
}

/// Fingerprints hashable values with BLAKE3, prefixed by a caller-provided seed.
pub struct Fingerprinter {
    seed: String,
}

impl Fingerprinter {
    pub fn new(seed: impl Into<String>) -> Self {
        Fingerprinter { seed: seed.into() }
    }

    pub fn of<T: Hash + ?Sized>(&self, value: &T) -> Key {
        let mut hasher = Blake3Hasher(blake3::Hasher::new());
        hasher.0.update(self.seed.as_bytes());
        value.hash(&mut hasher);
        Key(*hasher.0.finalize().as_bytes())
    }
}

/// Adapter letting `std::hash::Hash` feed BLAKE3 (BLAKE3 has no `Hasher` impl of its own).
struct Blake3Hasher(blake3::Hasher);

impl Hasher for Blake3Hasher {
    fn finish(&self) -> u64 {
        let digest = self.0.finalize();
        u64::from_le_bytes(digest.as_bytes()[..8].try_into().expect("digest is 32 bytes"))
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
        let printer = Fingerprinter::new("seed");
        assert_eq!(printer.of("gate"), printer.of("gate"));
        assert_ne!(printer.of("gate"), printer.of("door"));
        assert_ne!(printer.of(&[1u8, 2, 3]), printer.of(&[1u8, 2, 4]));
    }

    #[test]
    fn different_seeds_do_not_collide() {
        assert_ne!(
            Fingerprinter::new("a").of("x"),
            Fingerprinter::new("b").of("x")
        );
    }
}
