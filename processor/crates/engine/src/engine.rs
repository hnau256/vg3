//! The `Body -> Part` transformation, and the Merkle key that the cache is keyed by.

use std::sync::OnceLock;

use vg3_cache::{get_or_put, Cache, Fingerprinter, Key};
use vg3_model::{Color, Model, Body, BodyIndex, Sketch, SketchIndex};

use crate::error::{Error, Result};
use crate::sys::ffi;

mod contour;
mod expression;
mod fillet;
mod fillet2d;
mod info;
mod math2d;
mod math3d;
mod op;
mod radius;
mod sketch;
mod part;

pub use part::{BrepCodec, Part};

use op::Evaluate;
use sketch::{build_regions, Region};

/// An exported part: its built geometry plus the name and optional color from the `export` list.
pub struct Output {
    pub name: String,
    pub color: Option<Color>,
    pub part: Part,
}

/// Evaluates exactly what the model's `export` list names, in order, using the given cache.
///
/// The cache is domain-agnostic; the engine's own `BrepCodec` (its `Part` <-> bytes conversion) is
/// public so the caller can build a disk-backed cache with it.
pub fn evaluate<C: Cache<Key, Part> + ?Sized>(model: &Model, cache: &mut C) -> Result<Vec<Output>> {
    // Validate every reference (`index < current`) up front — bodies and sketches alike.
    for (index, sketch) in model.sketches.iter().enumerate() {
        sketch.try_map(|operand| -> Result<()> { validate_index(operand.value(), index) })?;
    }
    for (index, body) in model.bodies.iter().enumerate() {
        body.try_map(|operand| -> Result<()> { validate_index(operand.value(), index) })?;
        for validated in body.map_sketches(|sketch| validate_index(sketch.value(), model.sketches.len())) {
            validated?;
        }
    }

    let regions = build_regions(&model.sketches)?;
    let bodies = &model.bodies;
    let mut outputs = Vec::new();
    for item in &model.export {
        let index = item.index.value();
        let body = bodies.get(index).ok_or(Error::ExportIndex {
            index,
            bodies: bodies.len(),
        })?;
        outputs.push(Output {
            name: item.name.clone(),
            color: item.color,
            part: get_or_evaluate(body, bodies, &model.sketches, &regions, cache)?,
        });
    }
    Ok(outputs)
}

/// The whole `Body -> Part` transformation. The cache key is a purely internal detail: computed
/// here, right before use, and never leaving this function. The cache is whatever the caller
/// passed in — memory, disk, a layering of both, or nothing.
fn get_or_evaluate<C: Cache<Key, Part> + ?Sized>(
    body: &Body<BodyIndex>,
    bodies: &[Body<BodyIndex>],
    sketches: &[Sketch<SketchIndex>],
    regions: &[Region],
    cache: &mut C,
) -> Result<Part> {
    let key = key_of(body, bodies, sketches)?;
    get_or_put(cache, &key, |cache| {
        let ready: Body<Part> = body.try_map(|operand| {
            get_or_evaluate(&bodies[operand.value()], bodies, sketches, regions, cache)
        })?;
        ready.evaluate(regions)
    })
}

/// The body's Merkle key `H(body ‖ operand_keys… ‖ sketch_keys…)`, computed on demand (keys are not
/// stored). References are `index < current` (checked in [`evaluate`]), so indexing is in range.
fn key_of(
    body: &Body<BodyIndex>,
    bodies: &[Body<BodyIndex>],
    sketches: &[Sketch<SketchIndex>],
) -> Result<Key> {
    let mapped: Body<Key> =
        body.try_map(|operand| key_of(&bodies[operand.value()], bodies, sketches))?;
    let sketch_keys = body
        .map_sketches(|sketch| key_of_sketch(&sketches[sketch.value()], sketches))
        .into_iter()
        .collect::<Result<Vec<Key>>>()?;
    Ok(fingerprinter().of(&BodyKeyInput {
        body: &mapped,
        sketches: sketch_keys,
    }))
}

/// The sketch's Merkle key `H(sketch ‖ operand_keys…)`.
fn key_of_sketch(
    sketch: &Sketch<SketchIndex>,
    sketches: &[Sketch<SketchIndex>],
) -> Result<Key> {
    let mapped: Sketch<Key> =
        sketch.try_map(|operand| key_of_sketch(&sketches[operand.value()], sketches))?;
    Ok(fingerprinter().of(&mapped))
}

/// The hashable input of a body's key: the body (with operand keys in place) plus the keys of the
/// sketches it references, so a body's key depends on sketch content, not just the arena index.
#[derive(Hash)]
struct BodyKeyInput<'a> {
    body: &'a Body<Key>,
    sketches: Vec<Key>,
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
    use vg3_model::{Export, Scalar};

    fn scalar(value: f64) -> Scalar {
        Scalar::try_from(value).expect("finite")
    }

    fn box_node(size: f64) -> Body<BodyIndex> {
        Body::Box {
            width: scalar(size),
            length: scalar(size),
            height: scalar(size),
        }
    }

    fn export(index: usize) -> Export {
        Export {
            index: BodyIndex::new(index),
            name: format!("p{index}"),
            color: None,
        }
    }

    fn model(parts: Vec<Body<BodyIndex>>) -> Model {
        let export = (0..parts.len()).map(export).collect();
        Model {
            version: 1,
            sketches: Vec::new(),
            bodies: parts,
            export,
        }
    }

    fn box_part(size: f64) -> Part {
        evaluate(
            &model(vec![box_node(size)]),
            &mut vg3_cache::Memory::default(),
        )
        .expect("builds")
        .pop()
        .expect("one root")
        .part
    }

    #[test]
    fn identical_subtrees_are_built_once() {
        let outputs = evaluate(
            &model(vec![box_node(2.0), box_node(2.0)]),
            &mut vg3_cache::Memory::default(),
        )
        .expect("builds");
        assert_eq!(outputs.len(), 2);
        assert!(
            outputs[0].part.shares_storage(&outputs[1].part),
            "the cache must reuse an identical body"
        );
    }

    #[test]
    fn engine_reads_from_disk() {
        let directory = std::env::temp_dir().join("vg3-engine-disk-test");
        let _ = std::fs::remove_dir_all(&directory);

        // Poison the key of a 2x2x2 box with a 1x1x1 box: if the engine consults the disk layer,
        // the result must be the small box.
        let model = model(vec![box_node(2.0)]);
        let key = key_of(&model.bodies[0], &model.bodies, &model.sketches).expect("key");
        let mut disk = vg3_cache::Disk::new(directory.clone(), BrepCodec);
        disk.put(&key, &box_part(1.0));

        let outputs = evaluate(
            &model,
            &mut vg3_cache::Disk::new(directory.clone(), BrepCodec)
                .wrap_with(vg3_cache::Memory::default()),
        )
        .expect("builds");
        assert!(
            (outputs[0].part.volume() - 1.0).abs() < 1e-9,
            "engine must have read the disk entry"
        );
        let _ = std::fs::remove_dir_all(&directory);
    }
}
