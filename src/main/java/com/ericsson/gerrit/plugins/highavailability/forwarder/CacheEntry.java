// Copyright (C) 2018 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.ericsson.gerrit.plugins.highavailability.forwarder;

import com.ericsson.gerrit.plugins.highavailability.cache.Constants;

/** Represents a cache entry to evict */
public class CacheEntry {
  private final String pluginName;
  private final String cacheName;
  private final Object key;

  /**
   * Cache entry
   *
   * @param pluginName the plugin name to which the cache belongs, or "gerrit" for a Gerrit core
   *     cache
   * @param cacheName the name of the cache to evict the entry from
   * @param key the key identifying the entry in the cache
   */
  public CacheEntry(String pluginName, String cacheName, Object key) {
    this.pluginName = pluginName;
    this.cacheName = cacheName;
    this.key = key;
  }

  public String getPluginName() {
    return pluginName;
  }

  public String getCacheName() {
    return cacheName;
  }

  public Object getKey() {
    return key;
  }

  /**
   * Build a CacheEntry from the specified cache and key
   *
   * @param cache String representing the cache, e.g. my_plugin.my_cache
   * @param key The Object representing the key
   * @return the CacheEntry
   */
  public static CacheEntry from(String cache, Object key) {
    int dot = cache.indexOf('.');
    if (dot > 0) {
      return new CacheEntry(cache.substring(0, dot), cache.substring(dot + 1), key);
    }
    return new CacheEntry(Constants.GERRIT, cache, key);
  }
}
