//! The sketch (2D) node — a flat arena of these is the planar part of the model.

use serde::Deserialize;

use crate::curve::Curve2;
use crate::op::{BooleanKind, TransformOp2};
use crate::value::{NonEmpty, Vec2};

/// A planar node: a contour, booleans over them and planar transforms.
///
/// There is no `rect`/`circle` node: OpenCASCADE has no such primitives, so the DSL builds them as
/// `Polygon`/`Contour` — the format stays OCCT-idiomatic.
///
/// `T` is the operand type — `SketchIndex` in the wire model (back-references within the sketch
/// arena); sketches never reference bodies.
#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Sketch<T> {
    Polygon {
        points: NonEmpty<Vec2>,
    },
    Contour {
        start: Vec2,
        edges: NonEmpty<Curve2>,
    },
    Bool {
        kind: BooleanKind,
        arguments: NonEmpty<T>,
        tools: NonEmpty<T>,
    },
    Transform {
        target: T,
        op: TransformOp2,
    },
}

impl<T: Clone> Sketch<T> {
    /// Functor over operands (the planar counterpart of [`crate::Body::try_map`]).
    pub fn try_map<U, E>(
        &self,
        mut f: impl FnMut(T) -> std::result::Result<U, E>,
    ) -> std::result::Result<Sketch<U>, E> {
        Ok(match self {
            Sketch::Polygon { points } => Sketch::Polygon {
                points: points.clone(),
            },
            Sketch::Contour { start, edges } => Sketch::Contour {
                start: *start,
                edges: edges.clone(),
            },
            Sketch::Bool {
                kind,
                arguments,
                tools,
            } => Sketch::Bool {
                kind: *kind,
                arguments: arguments.try_map(&mut f)?,
                tools: tools.try_map(&mut f)?,
            },
            Sketch::Transform { target, op } => Sketch::Transform {
                target: f(target.clone())?,
                op: op.clone(),
            },
        })
    }
}
