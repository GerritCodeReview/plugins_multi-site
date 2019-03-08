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

package com.googlesource.gerrit.plugins.multisite.forwarder;

import static com.googlesource.gerrit.plugins.multisite.MultiSiteLogFile.multisiteLog;

import com.google.common.cache.Cache;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.cache.Constants;

/**
 * Evict cache entries. This class is meant to be used on the receiving side of the {@link
 * CacheEvictionForwarder} since it will prevent cache evictions to be forwarded again causing an
 * infinite forwarding loop between the 2 nodes.
 */
@Singleton
public class ForwardedCacheEvictionHandler {

  private final DynamicMap<Cache<?, ?>> cacheMap;

  @Inject
  public ForwardedCacheEvictionHandler(DynamicMap<Cache<?, ?>> cacheMap) {
    this.cacheMap = cacheMap;
  }

  /**
   * Evict an entry from the cache of the local node, eviction will not be forwarded to the other
   * node.
   *
   * @param entry the cache entry to evict
   * @throws CacheNotFoundException if cache does not exist
   */
  public void evict(CacheEntry entry) throws CacheNotFoundException {
    Cache<?, ?> cache = cacheMap.get(entry.getPluginName(), entry.getCacheName());
    if (cache == null) {
      throw new CacheNotFoundException(entry.getPluginName(), entry.getCacheName());
    }
    try {
      Context.setForwardedEvent(true);
      if (Constants.PROJECT_LIST.equals(entry.getCacheName())) {
        // One key is holding the list of projects
        cache.invalidateAll();
        multisiteLog.debug("Invalidated cache {}", entry.getCacheName());
      } else {
        cache.invalidate(entry.getKey());
        multisiteLog.debug("Invalidated cache {}[{}]", entry.getCacheName(), entry.getKey());
      }
    } finally {
      Context.unsetForwardedEvent();
    }
  }
}
