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

package com.googlesource.gerrit.plugins.multisite.event;

import static org.mockito.Mockito.verify;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.events.EventGsonProvider;
import com.google.gson.Gson;
import com.googlesource.gerrit.plugins.multisite.cache.Constants;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheEntry;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheKeyJsonParser;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedCacheEvictionHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.CacheEvictionEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.CacheEvictionEventRouter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CacheEvictionEventRouterTest {

  private CacheEvictionEventRouter router;
  @Mock private ForwardedCacheEvictionHandler cacheEvictionHandler;
  Gson gson;

  @Before
  public void setUp() {
    gson = new EventGsonProvider().get();
    router = new CacheEvictionEventRouter(cacheEvictionHandler, new CacheKeyJsonParser(gson));
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_CacheEviction() throws Exception {
    final CacheEvictionEvent event = new CacheEvictionEvent("cache", "key");
    router.route(event);

    verify(cacheEvictionHandler).evict(CacheEntry.from(event.cacheName, event.key));
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_CacheEvictionWithSlash()
      throws Exception {
    final CacheEvictionEvent event = new CacheEvictionEvent("cache", "some/key");
    router.route(event);

    verify(cacheEvictionHandler).evict(CacheEntry.from(event.cacheName, event.key));
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_ProjectCacheEvictionWithSlash()
      throws Exception {
    final CacheEvictionEvent event = new CacheEvictionEvent(Constants.PROJECTS, "some/project");
    router.route(event);

    verify(cacheEvictionHandler)
        .evict(CacheEntry.from(event.cacheName, Project.nameKey((String) event.key)));
  }
}
