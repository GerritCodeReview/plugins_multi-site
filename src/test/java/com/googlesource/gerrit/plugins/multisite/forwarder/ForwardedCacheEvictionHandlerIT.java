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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gerrit.server.events.EventGson;
import com.google.gerrit.server.project.ProjectCacheImpl;
import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.cache.CacheModule;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.CacheEvictionEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.CacheEvictionEventRouter;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.RouterModule;
import com.googlesource.gerrit.plugins.multisite.index.IndexModule;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@TestPlugin(
    name = "multi-site",
    sysModule =
        "com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedCacheEvictionHandlerIT$TestModule")
public class ForwardedCacheEvictionHandlerIT extends LightweightPluginDaemonTest {
  private static final Duration CACHE_EVICTIONS_WAIT_TIMEOUT = Duration.ofMinutes(1);

  @SuppressWarnings("rawtypes")
  @Inject
  private DynamicSet<CacheRemovalListener> cacheRemovalListeners;

  @Inject private CacheEvictionEventRouter objectUnderTest;
  @Inject @EventGson private Gson gson;
  private CacheEvictionsTracker<?, ?> evictionsCacheTracker;
  private RegistrationHandle cacheEvictionRegistrationHandle;

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
    }
  }

  public static class CacheEvictionsTracker<K, V> implements CacheRemovalListener<K, V> {
    private final Map<String, Set<Object>> trackedEvictions;
    private final CountDownLatch allExpectedEvictionsArrived;

    public CacheEvictionsTracker(int numExpectedEvictions) {
      allExpectedEvictionsArrived = new CountDownLatch(numExpectedEvictions);
      trackedEvictions = Maps.newHashMap();
    }

    public Set<Object> trackedEvictionsFor(String cacheName) {
      return trackedEvictions.getOrDefault(cacheName, Collections.emptySet());
    }

    public void waitForExpectedEvictions() throws InterruptedException {
      allExpectedEvictionsArrived.await(
          CACHE_EVICTIONS_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void onRemoval(
        String pluginName, String cacheName, RemovalNotification<K, V> notification) {
      trackedEvictions.compute(
          cacheName,
          (k, v) -> {
            if (v == null) {
              return Sets.newHashSet(notification.getKey());
            }
            v.add(notification.getKey());
            return v;
          });
      allExpectedEvictionsArrived.countDown();
    }
  }

  @Before
  public void startTrackingCacheEvictions() {
    evictionsCacheTracker = new CacheEvictionsTracker<>(1);
    cacheEvictionRegistrationHandle = cacheRemovalListeners.add("gerrit", evictionsCacheTracker);
  }

  @After
  public void stopTrackingCacheEvictions() {
    cacheEvictionRegistrationHandle.remove();
  }

  @Test
  public void shouldEvictProjectCache() throws Exception {
    objectUnderTest.route(new CacheEvictionEvent(ProjectCacheImpl.CACHE_NAME, project.get()));
    evictionsCacheTracker.waitForExpectedEvictions();

    assertThat(evictionsCacheTracker.trackedEvictionsFor(ProjectCacheImpl.CACHE_NAME))
        .contains(project);
  }

  @Test
  public void shouldEvictProjectCacheWithSlash() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("my/project");
    gApi.projects().create(in);
    Project.NameKey projectNameKey = Project.nameKey(in.name);

    objectUnderTest.route(
        new CacheEvictionEvent(ProjectCacheImpl.CACHE_NAME, projectNameKey.get()));

    evictionsCacheTracker.waitForExpectedEvictions();
    assertThat(evictionsCacheTracker.trackedEvictionsFor(ProjectCacheImpl.CACHE_NAME))
        .contains(projectNameKey);
  }
}
