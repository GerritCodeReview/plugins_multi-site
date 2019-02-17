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

package com.googlesource.gerrit.plugins.multisite.forwarder.rest;

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheEvictionForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.CacheEvictionEvent;
import com.googlesource.gerrit.plugins.multisite.peers.PeerInfo;
import java.util.Set;

@Singleton
public class RestCacheEvictionForwarder extends AbstractRestForwarder
    implements CacheEvictionForwarder {
  @Inject
  RestCacheEvictionForwarder(
      HttpSession httpClient,
      @PluginName String pluginName,
      Configuration cfg,
      Provider<Set<PeerInfo>> peerInfoProvider) {
    super(httpClient, pluginName, cfg, peerInfoProvider);
  }

  @Override
  public boolean evict(CacheEvictionEvent cacheEvictionEvent) {
    String json = GsonParser.toJson(cacheEvictionEvent.cacheName, cacheEvictionEvent.key);
    return post(
        "invalidate cache " + cacheEvictionEvent.cacheName,
        "cache",
        cacheEvictionEvent.cacheName,
        json);
  }
}
