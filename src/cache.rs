use std::collections::HashMap;
use std::hash::Hash;

use crate::error::Result;

/// A tiny key/value memo. Knows nothing about the model: just `get_or_put`.
pub struct Cache<K, V>(HashMap<K, V>);

impl<K, V> Default for Cache<K, V> {
    fn default() -> Self {
        Cache(HashMap::new())
    }
}

impl<K: Eq + Hash, V: Clone> Cache<K, V> {
    /// Returns the cached value for `key`, otherwise computes it with `compute` (which may
    /// re-enter the cache) and stores the result.
    pub fn get_or_put(
        &mut self,
        key: K,
        compute: impl FnOnce(&mut Self) -> Result<V>,
    ) -> Result<V> {
        if let Some(value) = self.0.get(&key) {
            return Ok(value.clone());
        }
        let value = compute(&mut *self)?;
        self.0.insert(key, value.clone());
        Ok(value)
    }
}
