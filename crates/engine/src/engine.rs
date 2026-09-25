//! The `Node -> Part` transformation, and the Merkle key that the cache is keyed by.

use std::collections::HashSet;
use std::sync::OnceLock;

use vg3_cache::{Cache, Fingerprinter, Key};
use vg3_model::{Model, Node};

use crate::error::{Error, Result};
use crate::sys::ffi;

mod contour;
mod fillet;
mod op;
mod part;

pub use part::{BrepCodec, Part};

use op::Evaluate;

/// Evaluates the model's roots. The cache is not passed in directly: the engine owns the
/// `Part` <-> bytes codec (its `BrepCodec`), hands it to `make_cache`, and uses whatever cache the
/// factory builds. This keeps the cache fully unaware of the domain model.
pub fn evaluate<C, F>(model: &Model, make_cache: F) -> Result<Vec<Part>>
where
    C: Cache<Key, Part>,
    F: FnOnce(BrepCodec) -> C,
{
    // One `try_map` pass over the arena: validate every reference and collect reachability.
    let mut referenced = HashSet::new();
    for (index, node) in model.parts.iter().enumerate() {
        node.try_map(|operand| -> Result<()> {
            validate_index(operand, index)?;
            referenced.insert(operand);
            Ok(())
        })?;
    }

    // Roots are the nodes nobody references.
    let mut cache = make_cache(BrepCodec);
    let parts = &model.parts;
    let mut roots = Vec::new();
    for (index, node) in parts.iter().enumerate() {
        if !referenced.contains(&index) {
            roots.push(get_or_evaluate(node, parts, &mut cache)?);
        }
    }
    Ok(roots)
}

/// The whole `Node -> Part` transformation. The cache key is a purely internal detail: computed
/// here, right before use, and never leaving this function. The cache is whatever the caller
/// passed in — memory, disk, a layering of both, or nothing.
fn get_or_evaluate<C: Cache<Key, Part>>(
    node: &Node<usize>,
    parts: &[Node<usize>],
    cache: &mut C,
) -> Result<Part> {
    let key = key_of(node, parts)?;
    cache.get_or_put(key, |cache| {
        let ready: Node<Part> =
            node.try_map(|operand| get_or_evaluate(&parts[operand], parts, cache))?;
        ready.evaluate()
    })
}

/// The node's Merkle key `H(node ‖ operand_keys…)`, computed on demand (keys are not stored).
/// References are `index < current` (checked in [`evaluate`]), so `parts[operand]` is in range.
fn key_of(node: &Node<usize>, parts: &[Node<usize>]) -> Result<Key> {
    let mapped: Node<Key> = node.try_map(|operand| key_of(&parts[operand], parts))?;
    Ok(fingerprinter().of(&mapped))
}

/// Version seed mixed into every key: the tool version plus the OpenCASCADE version, so cached
/// entries are not reused after a semantics change.
fn fingerprinter() -> &'static Fingerprinter {
    static FINGERPRINTER: OnceLock<Fingerprinter> = OnceLock::new();
    FINGERPRINTER.get_or_init(|| {
        Fingerprinter::new(format!(
            "vg3-ir1; vg3 {}; occt {}",
            env!("CARGO_PKG_VERSION"),
            ffi::occt_version()
        ))
    })
}

fn validate_index(index: usize, current: usize) -> Result<()> {
    if index >= current {
        Err(Error::InvalidReference { index, current })
    } else {
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use vg3_model::Scalar;

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

    fn build(
        model: &Model,
        make_cache: impl FnOnce(BrepCodec) -> vg3_cache::Memory<Key, Part>,
    ) -> Vec<Part> {
        evaluate(model, make_cache).expect("builds")
    }

    fn box_part(size: f64) -> Part {
        build(
            &Model {
                version: 1,
                parts: vec![box_node(size)],
            },
            |_| vg3_cache::Memory::default(),
        )
        .pop()
        .expect("one root")
    }

    #[test]
    fn identical_subtrees_are_built_once() {
        let model = Model {
            version: 1,
            parts: vec![box_node(2.0), box_node(2.0)],
        };
        let parts = build(&model, |_| vg3_cache::Memory::default());
        assert_eq!(parts.len(), 2);
        assert!(
            parts[0].shares_storage(&parts[1]),
            "the cache must reuse an identical node"
        );
    }

    #[test]
    fn engine_reads_from_disk() {
        let directory = std::env::temp_dir().join("vg3-engine-disk-test");
        let _ = std::fs::remove_dir_all(&directory);

        // Poison the key of a 2x2x2 box with a 1x1x1 box: if the engine consults the disk layer,
        // the result must be the small box.
        let model = Model {
            version: 1,
            parts: vec![box_node(2.0)],
        };
        let key = key_of(&model.parts[0], &model.parts).expect("key");
        let mut disk = vg3_cache::Disk::new(directory.clone(), BrepCodec);
        disk.put(&key, &box_part(1.0));

        let parts = evaluate(&model, |codec| {
            vg3_cache::Disk::new(directory.clone(), codec)
                .wrap_with(vg3_cache::Memory::default())
        })
        .expect("builds");
        assert!(
            (parts[0].volume() - 1.0).abs() < 1e-9,
            "engine must have read the disk entry"
        );
        let _ = std::fs::remove_dir_all(&directory);
    }
}
