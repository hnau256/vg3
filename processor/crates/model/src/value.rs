//! Canonical value types: scalars, angles, points, vectors and normals.

use std::hash::Hash;

use serde::Deserialize;

use crate::error::{Error, Result};

/// A list guaranteed to contain at least one element.
///
/// On the wire it is a plain JSON array, but deserialization rejects an empty array (canonicalized
/// in the type), and its JSON Schema carries `minItems: 1`.
#[derive(Clone, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[cfg_attr(feature = "schema", schemars(inline, extend("minItems" = 1)))]
#[serde(try_from = "Vec<T>")]
pub struct NonEmpty<T>(Vec<T>);

impl<T> NonEmpty<T> {
    pub fn as_slice(&self) -> &[T] {
        &self.0
    }

    pub fn iter(&self) -> std::slice::Iter<'_, T> {
        self.0.iter()
    }

    pub fn len(&self) -> usize {
        self.0.len()
    }
}

impl<T: Clone> NonEmpty<T> {
    /// Maps every element, preserving non-emptiness (mirrors the `Body::try_map` functor).
    pub fn try_map<U, E>(
        &self,
        mut f: impl FnMut(T) -> std::result::Result<U, E>,
    ) -> std::result::Result<NonEmpty<U>, E> {
        let mapped = self
            .0
            .iter()
            .cloned()
            .map(&mut f)
            .collect::<std::result::Result<Vec<_>, E>>()?;
        Ok(NonEmpty(mapped))
    }
}

impl<T> TryFrom<Vec<T>> for NonEmpty<T> {
    type Error = Error;

    fn try_from(value: Vec<T>) -> Result<Self> {
        if value.is_empty() {
            return Err(Error::EmptyList);
        }
        Ok(NonEmpty(value))
    }
}

/// A plain newtype over `usize` referencing an earlier node in an arena (`index < current`); on the
/// wire it is just an integer. Correctness is checked during evaluation, not at construction.
macro_rules! index_type {
    ($(#[$meta:meta])* $name:ident) => {
        $(#[$meta])*
        #[derive(Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Debug, Deserialize)]
        #[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
        #[serde(transparent)]
        pub struct $name(usize);

        impl $name {
            pub fn value(self) -> usize {
                self.0
            }

            pub fn new(index: usize) -> Self {
                $name(index)
            }
        }
    };
}

index_type!(
    /// A back-reference to an earlier body in the arena (`index < current`).
    BodyIndex
);

index_type!(
    /// A back-reference to an earlier sketch in the arena (`index < current`).
    SketchIndex
);

/// A finite scalar, canonicalized at deserialization (`−0.0 → +0.0`).
macro_rules! canonical_scalar {
    ($(#[$meta:meta])* $name:ident) => {
        $(#[$meta])*
        #[derive(Clone, Copy, PartialEq, Debug, Deserialize)]
        #[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
        #[cfg_attr(feature = "schema", schemars(with = "f64"))]
        #[serde(try_from = "f64")]
        pub struct $name(f64);

        impl $name {
            pub fn value(self) -> f64 {
                self.0
            }
        }

        impl Hash for $name {
            fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
                self.0.to_bits().hash(state);
            }
        }

        impl TryFrom<f64> for $name {
            type Error = Error;

            fn try_from(value: f64) -> Result<Self> {
                if !value.is_finite() {
                    return Err(Error::NonFiniteScalar);
                }
                Ok($name(if value == 0.0 { 0.0 } else { value }))
            }
        }
    };
}

canonical_scalar!(Scalar);

canonical_scalar!(Angle);

#[derive(Clone, Copy, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(deny_unknown_fields)]
pub struct Vec2 {
    pub x: Scalar,
    pub y: Scalar,
}

#[derive(Clone, Copy, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(deny_unknown_fields)]
pub struct Vec3 {
    pub x: Scalar,
    pub y: Scalar,
    pub z: Scalar,
}

/// An RGB color (`0..1` per component); used by exporters that support color.
#[derive(Clone, Copy, PartialEq, Hash, Debug, Deserialize)]
#[cfg_attr(feature = "schema", derive(schemars::JsonSchema))]
#[serde(deny_unknown_fields)]
pub struct Color {
    pub r: Scalar,
    pub g: Scalar,
    pub b: Scalar,
}

impl Color {
    pub fn components(self) -> [f64; 3] {
        [self.r.value(), self.g.value(), self.b.value()]
    }
}
