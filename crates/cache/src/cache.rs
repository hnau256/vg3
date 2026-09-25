use std::collections::HashMap;
use std::hash::Hash;

use crate::error::Result;

/// Universal key/value cache. Implementations differ only in *where* entries live.
pub trait Cache<K, V> {
    fn get(&mut self, key: &K) -> Option<V>;
    fn put(&mut self, key: &K, value: &V);

    /// Returns the cached value, or computes it with `compute` (which may re-enter this cache)
    /// and stores the result. The evaluation flow lives here, so implementations stay trivial.
    fn get_or_put<E>(
        &mut self,
        key: K,
        compute: impl FnOnce(&mut Self) -> std::result::Result<V, E>,
    ) -> std::result::Result<V, E>
    where
        Self: Sized,
    {
        if let Some(hit) = self.get(&key) {
            return Ok(hit);
        }
        let value = compute(self)?;
        self.put(&key, &value);
        Ok(value)
    }

    /// Glues two caches: `self` is the backing, `front` is consulted first —
    /// e.g. `disk.wrap_with(memory)`.
    fn wrap_with<Front: Cache<K, V>>(self, front: Front) -> Layered<Front, Self>
    where
        Self: Sized,
    {
        Layered { front, back: self }
    }
}

/// A two-way conversion between `T` and bytes — the pair of closures a disk store would otherwise
/// need, bundled into a single entity (an `Iso`: `encode` / `decode`).
pub trait Codec<T> {
    fn encode(&self, value: &T) -> Result<Vec<u8>>;
    fn decode(&self, bytes: &[u8]) -> Result<T>;
}

/// Stores nothing: evaluation always runs from scratch.
#[derive(Default)]
pub struct Noop;

impl<K, V> Cache<K, V> for Noop {
    fn get(&mut self, _key: &K) -> Option<V> {
        None
    }

    fn put(&mut self, _key: &K, _value: &V) {}
}

/// In-memory cache.
pub struct Memory<K, V>(HashMap<K, V>);

impl<K, V> Default for Memory<K, V> {
    fn default() -> Self {
        Memory(HashMap::new())
    }
}

impl<K: Eq + Hash + Clone, V: Clone> Cache<K, V> for Memory<K, V> {
    fn get(&mut self, key: &K) -> Option<V> {
        self.0.get(key).cloned()
    }

    fn put(&mut self, key: &K, value: &V) {
        self.0.insert(key.clone(), value.clone());
    }
}

/// Two caches glued: reads try `front` then `back`; writes go to both.
pub struct Layered<Front, Back> {
    front: Front,
    back: Back,
}

impl<K, V, Front: Cache<K, V>, Back: Cache<K, V>> Cache<K, V> for Layered<Front, Back> {
    fn get(&mut self, key: &K) -> Option<V> {
        if let Some(value) = self.front.get(key) {
            return Some(value);
        }
        self.back.get(key)
    }

    fn put(&mut self, key: &K, value: &V) {
        self.front.put(key, value);
        self.back.put(key, value);
    }
}
