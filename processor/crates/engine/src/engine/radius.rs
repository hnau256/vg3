//! Compiles a `RadiusSpec` into a reusable, per-element radius evaluator.
//!
//! The expression (if any) is parsed once; only the element data changes between calls. Shared by
//! `fillet` and `fillet2d` (which use it for edges and corners respectively).

use vg3_model::RadiusSpec;

use crate::engine::expression::{Compiled, Context, Data};
use crate::error::Result;

pub(super) enum Radius {
    All(f64),
    Expression(Compiled),
    Selected(Compiled, f64),
}

impl Radius {
    /// Compiles `spec` against `context` once (the expression, if present, is parsed here).
    pub fn compile(context: &Context, spec: &RadiusSpec) -> Result<Self> {
        Ok(match spec {
            RadiusSpec::All { radius } => Radius::All(radius.value()),
            RadiusSpec::Expression { expression } => {
                Radius::Expression(context.compile(expression)?)
            }
            RadiusSpec::Selected { expression, radius } => {
                Radius::Selected(context.compile(expression)?, radius.value())
            }
        })
    }

    /// The radius for `element` (the expression is evaluated, if present).
    pub fn value(&self, context: &Context, element: Data) -> Result<f64> {
        Ok(match self {
            Radius::All(value) => *value,
            Radius::Expression(compiled) => context.evaluate(compiled, element)?,
            Radius::Selected(compiled, value) => {
                if context.evaluate::<bool>(compiled, element)? {
                    *value
                } else {
                    0.0
                }
            }
        })
    }
}
