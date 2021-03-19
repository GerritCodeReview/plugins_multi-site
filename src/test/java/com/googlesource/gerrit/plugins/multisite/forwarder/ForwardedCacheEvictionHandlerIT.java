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

import static com.google.common.truth.Truth.assertThat;

import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDbConfiguration;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.Sets;
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
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

@TestPlugin(
    name = "multi-site",
    sysModule =
        "com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedCacheEvictionHandlerIT$TestModule")
public class ForwardedCacheEvictionHandlerIT extends LightweightPluginDaemonTest {
  private static final Duration CACHE_EVICTION_TIMEOUT = Duration.ofMinutes(1);

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
    private static Map<String, Set<Object>> cacheEvictions = new ConcurrentHashMap<>();
    private static Optional<CountDownLatch> countDownLatch = Optional.empty();

    public static CountDownLatch startCountDown(int expectedCounts) {
      cacheEvictions.clear();
      countDownLatch = Optional.of(new CountDownLatch(expectedCounts));
      return countDownLatch.get();
    }

    public static Set<Object> cacheEvictions(String cacheName) {
      return cacheEvictions.getOrDefault(cacheName, Collections.emptySet());
    }

    @Override
    public void onRemoval(
        String pluginName, String cacheName, RemovalNotification<K, V> notification) {
      cacheEvictions.compute(
          cacheName,
          (k, v) -> {
            if (v == null) {
              return Sets.newHashSet(notification.getKey());
            }
            v.add(notification.getKey());
            return v;
          });
      countDownLatch.ifPresent(count -> count.countDown());
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
    CountDownLatch counter = CacheEvictionTest.startCountDown(1);
    objectUnderTest.route(
        new CacheEvictionEvent(ProjectCacheImpl.CACHE_NAME, gson.toJson(project)));
    counter.await(CACHE_EVICTION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    assertThat(CacheEvictionTest.cacheEvictions(ProjectCacheImpl.CACHE_NAME)).contains(project);
  }
}
