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
import com.google.common.cache.Cache;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evict cache entries. This class is meant to be used on the receiving side of the {@link
 * Forwarder} since it will prevent cache evictions to be forwarded again causing an infinite
 * forwarding loop between the 2 nodes.
 */
@Singleton
public class EvictCache {
  private static final Logger logger = LoggerFactory.getLogger(EvictCache.class);

  private final DynamicMap<Cache<?, ?>> cacheMap;

  @Inject
  public EvictCache(DynamicMap<Cache<?, ?>> cacheMap) {
    this.cacheMap = cacheMap;
  }

  /**
   * Evict an entry from the cache of the local node, eviction will not be forwarded to the other
   * node.
   *
   * @param pluginName the plugin name to which the cache belongs, or "gerrit" for a Gerrit core
   *     cache
   * @param cacheName the name of the cache to evict the entry from
   * @param key the key identifying the entry to evict
   * @throws CacheNotFoundException if cache does not exist
   */
  public void evict(String pluginName, String cacheName, Object key) throws CacheNotFoundException {
    Cache<?, ?> cache = cacheMap.get(pluginName, cacheName);
    if (cache == null) {
      throw new CacheNotFoundException(pluginName, cacheName);
    }
    try {
      Context.setForwardedEvent(true);
      if (Constants.PROJECT_LIST.equals(cacheName)) {
        // One key is holding the list of projects
        cache.invalidateAll();
        logger.debug("Invalidated cache {}", cacheName);
      } else {
        cache.invalidate(key);
        logger.debug("Invalidated cache {}[{}]", cacheName, key);
      }
    } finally {
      Context.unsetForwardedEvent();
    }
  }
}
