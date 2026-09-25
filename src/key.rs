//! Node fingerprinting — the Merkle cache key.
//!
//! This is model-aware (it hashes a `Node`), so it is deliberately kept out of the generic cache
//! ([`crate::cache`]). The hasher and the mixed-in version live here as well.

use std::hash::{Hash, Hasher};
use std::sync::OnceLock;

use crate::model::Node;
use crate::sys::ffi;

/// A node's cache key: a strong (BLAKE3) fingerprint of its whole subtree.
///
/// `H(version ‖ node ‖ operand_keys…)`: the node's own canonical parameters plus the
/// already-computed keys of its operands.
#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug)]
pub struct Key([u8; 32]);

impl Key {
    /// Lowercase hex — used as the on-disk file name.
    pub fn to_hex(self) -> String {
        let mut out = String::with_capacity(64);
        for byte in self.0 {
            out.push_str(&format!("{byte:02x}"));
        }
        out
    }
}

/// Fingerprints a node whose operands are already turned into their keys.
pub fn node_key(node: &Node<Key>) -> Key {
    let mut hasher = Blake3Hasher(blake3::Hasher::new());
    hasher.0.update(version_seed().as_bytes());
    node.hash(&mut hasher);
    Key(*hasher.0.finalize().as_bytes())
}

/// Version mixed into every key, so cached shapes are only reused under identical semantics
/// (vg3 format/engine + the OCCT version that produced them).
fn version_seed() -> &'static str {
    static SEED: OnceLock<String> = OnceLock::new();
    SEED.get_or_init(|| {
        format!(
            "vg3-ir1; engine {}; occt {}",
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
    use crate::model::Scalar;

    fn scalar(value: f64) -> Scalar {
        Scalar::try_from(value).expect("finite")
    }

    #[test]
    fn keys_are_deterministic_and_structure_sensitive() {
        let a: Node<Key> = Node::Sphere {
            radius: scalar(2.0),
        };
        let b: Node<Key> = Node::Sphere {
            radius: scalar(2.0),
        };
        let c: Node<Key> = Node::Sphere {
            radius: scalar(3.0),
        };
        assert_eq!(node_key(&a), node_key(&b));
        assert_ne!(node_key(&a), node_key(&c));
    }

    #[test]
    fn operand_keys_participate_in_the_hash() {
        let inner_a: Node<Key> = Node::Sphere {
            radius: scalar(1.0),
        };
        let inner_b: Node<Key> = Node::Sphere {
            radius: scalar(2.0),
        };
        let fuse_a: Node<Key> = Node::Fuse {
            parts: vec![node_key(&inner_a)],
        };
        let fuse_b: Node<Key> = Node::Fuse {
            parts: vec![node_key(&inner_b)],
        };
        assert_ne!(node_key(&fuse_a), node_key(&fuse_b));
    }
}
