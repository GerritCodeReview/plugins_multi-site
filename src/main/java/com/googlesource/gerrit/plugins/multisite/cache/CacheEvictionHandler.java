// Copyright (C) 2015 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.cache;

import com.google.common.cache.RemovalNotification;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheEvictionForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.Context;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwarderTask;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.CacheEvictionEvent;
import java.util.concurrent.Executor;

class CacheEvictionHandler<K, V> implements CacheRemovalListener<K, V> {
  private final Executor executor;
  private final DynamicSet<CacheEvictionForwarder> forwarders;
  private final CachePatternMatcher matcher;

  @Inject
  CacheEvictionHandler(
      DynamicSet<CacheEvictionForwarder> forwarders,
      @CacheExecutor Executor executor,
      CachePatternMatcher matcher) {
    this.forwarders = forwarders;
    this.executor = executor;
    this.matcher = matcher;
  }

  @Override
  public void onRemoval(String plugin, String cache, RemovalNotification<K, V> notification) {
    if (!Context.isForwardedEvent() && !notification.wasEvicted() && matcher.matches(cache)) {
      executor.execute(new CacheEvictionTask(new CacheEvictionEvent(cache, notification.getKey())));
    }
  }

  class CacheEvictionTask extends ForwarderTask {
    CacheEvictionEvent cacheEvictionEvent;

    CacheEvictionTask(CacheEvictionEvent cacheEvictionEvent) {
      this.cacheEvictionEvent = cacheEvictionEvent;
    }

    @Override
    public void run() {
      forwarders.forEach(f -> f.evict(this, cacheEvictionEvent));
    }

    @Override
    public String toString() {
      return String.format(
          "Evict key '%s' from cache '%s' in target instance",
          cacheEvictionEvent.key, cacheEvictionEvent.cacheName);
    }
  }
}
