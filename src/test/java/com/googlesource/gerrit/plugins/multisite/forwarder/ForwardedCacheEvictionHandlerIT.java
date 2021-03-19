// Copyright (C) 2021 The Android Open Source Project
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

import static com.google.gerrit.acceptance.WaitUtil.waitUntil;

import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDbConfiguration;
import com.google.common.cache.RemovalNotification;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gerrit.server.events.EventGson;
import com.google.gerrit.server.project.ProjectCacheImpl;
import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.googlesource.gerrit.plugins.multisite.cache.CacheModule;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.CacheEvictionEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.CacheEvictionEventRouter;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.RouterModule;
import com.googlesource.gerrit.plugins.multisite.index.IndexModule;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

@TestPlugin(
    name = "multi-site",
    sysModule =
        "com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedCacheEvictionHandlerIT$TestModule")
public class ForwardedCacheEvictionHandlerIT extends LightweightPluginDaemonTest {

  private static final Duration WAIT_PATIENCE = Duration.ofSeconds(10);

  public static class TestModule extends AbstractModule {

    @Override
    protected void configure() {
      install(new ForwarderModule());
      install(new CacheModule());
      install(new RouterModule());
      install(new IndexModule());
      SharedRefDbConfiguration sharedRefDbConfig =
          new SharedRefDbConfiguration(new Config(), "multi-site");
      bind(SharedRefDbConfiguration.class).toInstance(sharedRefDbConfig);
      DynamicSet.bind(binder(), CacheRemovalListener.class).to(CacheEvictionTest.class);
    }
  }

  public static class CacheEvictionTest<K, V> implements CacheRemovalListener<K, V> {
    private static Map<String, Integer> cacheEvictions = new ConcurrentHashMap<>();

    public static void clear() {
      cacheEvictions.clear();
    }

    public static int numCacheEvictions(String cacheName) {
      return cacheEvictions.getOrDefault(cacheName, 0);
    }

    @Override
    public void onRemoval(
        String pluginName, String cacheName, RemovalNotification<K, V> notification) {
      cacheEvictions.compute(cacheName, (k, v) -> v == null ? 1 : v.intValue() + 1);
    }
  }

  private CacheEvictionEventRouter objectUnderTest;
  private Gson gson;

  @Override
  public void setUpTestPlugin() throws Exception {
    super.setUpTestPlugin();
    Injector injector = plugin.getSysInjector();
    objectUnderTest = injector.getInstance(CacheEvictionEventRouter.class);
    gson = injector.getInstance(Key.get(Gson.class, EventGson.class));
  }

  @Test
  public void shouldEvictProjectCache() throws Exception {
    CacheEvictionTest.clear();
    objectUnderTest.route(
        new CacheEvictionEvent(ProjectCacheImpl.CACHE_NAME, gson.toJson(project)));
    waitUntil(
        () -> CacheEvictionTest.numCacheEvictions(ProjectCacheImpl.CACHE_NAME) > 0, WAIT_PATIENCE);
  }
}
