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

package com.googlesource.gerrit.plugins.multisite;

import static com.google.common.truth.Truth.assertThat;
import static com.googlesource.gerrit.plugins.multisite.Configuration.Cache.CACHE_SECTION;
import static com.googlesource.gerrit.plugins.multisite.Configuration.Cache.PATTERN_KEY;
import static com.googlesource.gerrit.plugins.multisite.Configuration.DEFAULT_THREAD_POOL_SIZE;
import static com.googlesource.gerrit.plugins.multisite.Configuration.Event.EVENT_SECTION;
import static com.googlesource.gerrit.plugins.multisite.Configuration.Forwarding.DEFAULT_SYNCHRONIZE;
import static com.googlesource.gerrit.plugins.multisite.Configuration.Forwarding.SYNCHRONIZE_KEY;
import static com.googlesource.gerrit.plugins.multisite.Configuration.Index.INDEX_SECTION;
import static com.googlesource.gerrit.plugins.multisite.Configuration.THREAD_POOL_SIZE_KEY;

import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDbConfiguration.SharedRefDatabase;
import com.google.common.collect.ImmutableList;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConfigurationTest {
  private static final String INVALID_BOOLEAN = "invalidBoolean";
  private static final String INVALID_INT = "invalidInt";
  private static final int THREAD_POOL_SIZE = 1;

  private Config globalPluginConfig;
  private Config replicationConfig;

  @Before
  public void setUp() {
    globalPluginConfig = new Config();
    replicationConfig = new Config();
  }

  private Configuration getConfiguration() {
    return new Configuration(globalPluginConfig, replicationConfig);
  }

  @Test
  public void testGetIndexThreadPoolSize() throws Exception {
    assertThat(getConfiguration().index().threadPoolSize()).isEqualTo(DEFAULT_THREAD_POOL_SIZE);

    globalPluginConfig.setInt(INDEX_SECTION, null, THREAD_POOL_SIZE_KEY, THREAD_POOL_SIZE);
    assertThat(getConfiguration().index().threadPoolSize()).isEqualTo(THREAD_POOL_SIZE);

    globalPluginConfig.setString(INDEX_SECTION, null, THREAD_POOL_SIZE_KEY, INVALID_INT);
    assertThat(getConfiguration().index().threadPoolSize()).isEqualTo(DEFAULT_THREAD_POOL_SIZE);
  }

  @Test
  public void testGetIndexSynchronize() throws Exception {
    assertThat(getConfiguration().index().synchronize()).isEqualTo(DEFAULT_SYNCHRONIZE);

    globalPluginConfig.setBoolean(INDEX_SECTION, null, SYNCHRONIZE_KEY, false);
    assertThat(getConfiguration().index().synchronize()).isFalse();

    globalPluginConfig.setBoolean(INDEX_SECTION, null, SYNCHRONIZE_KEY, true);
    assertThat(getConfiguration().index().synchronize()).isTrue();

    globalPluginConfig.setString(INDEX_SECTION, null, SYNCHRONIZE_KEY, INVALID_BOOLEAN);
    assertThat(getConfiguration().index().synchronize()).isTrue();
  }

  @Test
  public void testGetCacheThreadPoolSize() throws Exception {
    assertThat(getConfiguration().cache().threadPoolSize()).isEqualTo(DEFAULT_THREAD_POOL_SIZE);

    globalPluginConfig.setInt(CACHE_SECTION, null, THREAD_POOL_SIZE_KEY, THREAD_POOL_SIZE);
    assertThat(getConfiguration().cache().threadPoolSize()).isEqualTo(THREAD_POOL_SIZE);

    globalPluginConfig.setString(CACHE_SECTION, null, THREAD_POOL_SIZE_KEY, INVALID_INT);
    assertThat(getConfiguration().cache().threadPoolSize()).isEqualTo(DEFAULT_THREAD_POOL_SIZE);
  }

  @Test
  public void testGetCacheSynchronize() throws Exception {
    assertThat(getConfiguration().cache().synchronize()).isEqualTo(DEFAULT_SYNCHRONIZE);

    globalPluginConfig.setBoolean(CACHE_SECTION, null, SYNCHRONIZE_KEY, false);
    assertThat(getConfiguration().cache().synchronize()).isFalse();

    globalPluginConfig.setBoolean(CACHE_SECTION, null, SYNCHRONIZE_KEY, true);
    assertThat(getConfiguration().cache().synchronize()).isTrue();

    globalPluginConfig.setString(CACHE_SECTION, null, SYNCHRONIZE_KEY, INVALID_BOOLEAN);
    assertThat(getConfiguration().cache().synchronize()).isTrue();
  }

  @Test
  public void testGetEventSynchronize() throws Exception {
    assertThat(getConfiguration().event().synchronize()).isEqualTo(DEFAULT_SYNCHRONIZE);

    globalPluginConfig.setBoolean(EVENT_SECTION, null, SYNCHRONIZE_KEY, false);
    assertThat(getConfiguration().event().synchronize()).isFalse();

    globalPluginConfig.setBoolean(EVENT_SECTION, null, SYNCHRONIZE_KEY, true);
    assertThat(getConfiguration().event().synchronize()).isTrue();

    globalPluginConfig.setString(EVENT_SECTION, null, SYNCHRONIZE_KEY, INVALID_BOOLEAN);
    assertThat(getConfiguration().event().synchronize()).isTrue();
  }

  @Test
  public void testGetCachePatterns() throws Exception {
    globalPluginConfig.setStringList(
        CACHE_SECTION, null, PATTERN_KEY, ImmutableList.of("^my_cache.*", "other"));
    assertThat(getConfiguration().cache().patterns())
        .containsExactly("^my_cache.*", "other")
        .inOrder();
  }

  @Test
  public void testGetIgnoredRefs() throws Exception {
    globalPluginConfig.setStringList(
        SharedRefDatabase.SECTION,
        null,
        SharedRefDatabase.IGNORED_REFS,
        ImmutableList.of("refs/heads/foo", "refs/tags/bar"));
    assertThat(getConfiguration().getSharedRefDbConfiguration().getSharedRefDb().getIgnoredRefs())
        .containsExactly("refs/heads/foo", "refs/tags/bar");
  }

  @Test
  public void shouldReturnValidationErrorsWhenReplicationOnStartupIsEnabled() throws Exception {
    Config replicationConfig = new Config();
    replicationConfig.setBoolean("gerrit", null, "replicateOnStartup", true);
    assertThat(new Configuration(globalPluginConfig, replicationConfig).validate()).isNotEmpty();
  }
}
