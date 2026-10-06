//! A typed, domain-agnostic Rhai expression evaluator.
//!
//! An expression is evaluated against a [`Context`]: named [`Data`] blocks plus a Rhai engine (with
//! whatever functions the caller registered). This module knows nothing about the domain — not the
//! object, not its element.

use rhai::{AST, Dynamic, Engine, Map, Scope};

use crate::error::{Error, Result};

/// The result type of an expression (the typing is chosen by the caller).
pub(super) trait ExpressionResult: Sized {
    fn from_dynamic(value: Dynamic, source: &str) -> Result<Self>;
}

impl ExpressionResult for f64 {
    fn from_dynamic(value: Dynamic, source: &str) -> Result<Self> {
        let number = if let Some(number) = value.clone().try_cast::<f64>() {
            number
        } else if let Some(number) = value.clone().try_cast::<i64>() {
            number as f64
        } else {
            return Err(Error::Expression(format!("{source}: result is not a number")));
        };
        if number.is_nan() {
            return Err(Error::Expression(format!("{source}: result is NaN")));
        }
        Ok(number)
    }
}

impl ExpressionResult for bool {
    fn from_dynamic(value: Dynamic, source: &str) -> Result<Self> {
        value
            .try_cast::<bool>()
            .ok_or_else(|| Error::Expression(format!("{source}: result is not a boolean")))
    }
}

/// A named block of parameters visible to expressions (an edge, a body's bounds, an axis, …).
pub(super) struct Data {
    pub name: &'static str,
    pub value: Map,
}

impl Data {
    pub fn new(name: &'static str, value: Map) -> Self {
        Data { name, value }
    }
}

/// A compiled expression, evaluated once per element without re-parsing.
pub(super) struct Compiled {
    ast: AST,
    source: String,
}

/// A reusable evaluation context: a Rhai engine plus the data blocks visible to every expression.
pub(super) struct Context {
    engine: Engine,
    data: Vec<Data>,
}

impl Context {
    pub fn new(engine: Engine) -> Self {
        Context {
            engine,
            data: Vec::new(),
        }
    }

    pub fn with_data(mut self, data: Data) -> Self {
        self.data.push(data);
        self
    }

    /// Parses and compiles `source` once; the result is evaluated many times (per element).
    pub fn compile(&self, source: &str) -> Result<Compiled> {
        let ast = self
            .engine
            .compile_expression(source)
            .map_err(|error| Error::Expression(format!("{source}: {error}")))?;
        Ok(Compiled {
            ast,
            source: source.to_string(),
        })
    }

    /// Evaluates a compiled expression, with `element` bound on top of the context's own blocks.
    pub fn evaluate<T: ExpressionResult>(&self, compiled: &Compiled, element: Data) -> Result<T> {
        let mut scope = Scope::new();
        for data in &self.data {
            scope.push_constant(data.name, data.value.clone());
        }
        scope.push_constant(element.name, element.value);
        let value = self
            .engine
            .eval_ast_with_scope::<Dynamic>(&mut scope, &compiled.ast)
            .map_err(|error| Error::Expression(format!("{}: {error}", compiled.source)))?;
        T::from_dynamic(value, &compiled.source)
    }
}
