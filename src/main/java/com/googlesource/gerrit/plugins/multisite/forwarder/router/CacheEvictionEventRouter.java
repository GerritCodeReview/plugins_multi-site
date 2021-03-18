// Copyright (C) 2019 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.forwarder.router;

import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheEntry;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheKeyJsonParser;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheNotFoundException;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedCacheEvictionHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.CacheEvictionEvent;

public class CacheEvictionEventRouter implements ForwardedEventRouter<CacheEvictionEvent> {
  private final ForwardedCacheEvictionHandler cacheEvictionHanlder;
  private final CacheKeyJsonParser gsonParser;

  @Inject
  public CacheEvictionEventRouter(
      ForwardedCacheEvictionHandler cacheEvictionHanlder, CacheKeyJsonParser gsonParser) {
    this.cacheEvictionHanlder = cacheEvictionHanlder;
    this.gsonParser = gsonParser;
  }

  @Override
  public void route(CacheEvictionEvent cacheEvictionEvent) throws CacheNotFoundException {
    Object parsedKey = gsonParser.fromJson(cacheEvictionEvent.cacheName, cacheEvictionEvent.key);
    cacheEvictionHanlder.evict(CacheEntry.from(cacheEvictionEvent.cacheName, parsedKey));
  }
}
