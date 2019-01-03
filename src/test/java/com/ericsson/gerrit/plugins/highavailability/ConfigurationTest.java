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

package com.ericsson.gerrit.plugins.highavailability;

import static com.ericsson.gerrit.plugins.highavailability.Configuration.Cache.CACHE_SECTION;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Cache.PATTERN_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.DEFAULT_THREAD_POOL_SIZE;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Event.EVENT_SECTION;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Forwarding.DEFAULT_SYNCHRONIZE;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Forwarding.SYNCHRONIZE_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.HealthCheck.DEFAULT_HEALTH_CHECK_ENABLED;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.HealthCheck.ENABLE_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.HealthCheck.HEALTH_CHECK_SECTION;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Http.CONNECTION_TIMEOUT_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Http.DEFAULT_MAX_TRIES;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Http.DEFAULT_RETRY_INTERVAL;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Http.DEFAULT_TIMEOUT_MS;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Http.HTTP_SECTION;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Http.MAX_TRIES_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Http.PASSWORD_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Http.RETRY_INTERVAL_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Http.SOCKET_TIMEOUT_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Http.USER_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Index.INDEX_SECTION;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Main.DEFAULT_SHARED_DIRECTORY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Main.MAIN_SECTION;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Main.SHARED_DIRECTORY_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.PEER_INFO_SECTION;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.PeerInfoStatic.STATIC_SUBSECTION;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.PeerInfoStatic.URL_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.THREAD_POOL_SIZE_KEY;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.SitePaths;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConfigurationTest {
  private static final String INVALID_BOOLEAN = "invalidBoolean";
  private static final String INVALID_INT = "invalidInt";
  private static final String PLUGIN_NAME = "high-availability";
  private static final String PASS = "fakePass";
  private static final String USER = "fakeUser";
  private static final String URL = "http://fakeUrl";
  private static final List<String> URLS = ImmutableList.of(URL, "http://anotherUrl/");
  private static final int TIMEOUT = 5000;
  private static final int MAX_TRIES = 5;
  private static final int RETRY_INTERVAL = 1000;
  private static final int THREAD_POOL_SIZE = 1;
  private static final String SHARED_DIRECTORY = "/some/directory";
  private static final Path SHARED_DIR_PATH = Paths.get(SHARED_DIRECTORY);
  private static final String RELATIVE_SHARED_DIRECTORY = "relative/dir";
  private static final Path SITE_PATH = Paths.get("/site_path");

  @Mock private PluginConfigFactory pluginConfigFactoryMock;
  private Config globalPluginConfig;
  private SitePaths sitePaths;

  @Before
  public void setUp() throws IOException {
    globalPluginConfig = new Config();
    when(pluginConfigFactoryMock.getGlobalPluginConfig(PLUGIN_NAME)).thenReturn(globalPluginConfig);
    sitePaths = new SitePaths(SITE_PATH);
  }

  private Configuration getConfiguration() {
    return new Configuration(pluginConfigFactoryMock, PLUGIN_NAME, sitePaths);
  }

  @Test
  public void testGetUrls() throws Exception {
    assertThat(getConfiguration().peerInfoStatic().urls()).isEmpty();

    globalPluginConfig.setStringList(PEER_INFO_SECTION, STATIC_SUBSECTION, URL_KEY, URLS);
    assertThat(getConfiguration().peerInfoStatic().urls())
        .containsAllIn(ImmutableList.of(URL, "http://anotherUrl"));
  }

  @Test
  public void testGetUser() throws Exception {
    assertThat(getConfiguration().http().user()).isEmpty();

    globalPluginConfig.setString(HTTP_SECTION, null, USER_KEY, USER);
    assertThat(getConfiguration().http().user()).isEqualTo(USER);
  }

  @Test
  public void testGetPassword() throws Exception {
    assertThat(getConfiguration().http().password()).isEmpty();

    globalPluginConfig.setString(HTTP_SECTION, null, PASSWORD_KEY, PASS);
    assertThat(getConfiguration().http().password()).isEqualTo(PASS);
  }

  @Test
  public void testGetConnectionTimeout() throws Exception {
    assertThat(getConfiguration().http().connectionTimeout()).isEqualTo(DEFAULT_TIMEOUT_MS);

    globalPluginConfig.setInt(HTTP_SECTION, null, CONNECTION_TIMEOUT_KEY, TIMEOUT);
    assertThat(getConfiguration().http().connectionTimeout()).isEqualTo(TIMEOUT);

    globalPluginConfig.setString(HTTP_SECTION, null, CONNECTION_TIMEOUT_KEY, INVALID_INT);
    assertThat(getConfiguration().http().connectionTimeout()).isEqualTo(DEFAULT_TIMEOUT_MS);
  }

  @Test
  public void testGetSocketTimeout() throws Exception {
    assertThat(getConfiguration().http().socketTimeout()).isEqualTo(DEFAULT_TIMEOUT_MS);

    globalPluginConfig.setInt(HTTP_SECTION, null, SOCKET_TIMEOUT_KEY, TIMEOUT);
    assertThat(getConfiguration().http().socketTimeout()).isEqualTo(TIMEOUT);

    globalPluginConfig.setString(HTTP_SECTION, null, SOCKET_TIMEOUT_KEY, INVALID_INT);
    assertThat(getConfiguration().http().socketTimeout()).isEqualTo(DEFAULT_TIMEOUT_MS);
  }

  @Test
  public void testGetMaxTries() throws Exception {
    assertThat(getConfiguration().http().maxTries()).isEqualTo(DEFAULT_MAX_TRIES);

    globalPluginConfig.setInt(HTTP_SECTION, null, MAX_TRIES_KEY, MAX_TRIES);
    assertThat(getConfiguration().http().maxTries()).isEqualTo(MAX_TRIES);

    globalPluginConfig.setString(HTTP_SECTION, null, MAX_TRIES_KEY, INVALID_INT);
    assertThat(getConfiguration().http().maxTries()).isEqualTo(DEFAULT_MAX_TRIES);
  }

  @Test
  public void testGetRetryInterval() throws Exception {
    assertThat(getConfiguration().http().retryInterval()).isEqualTo(DEFAULT_RETRY_INTERVAL);

    globalPluginConfig.setInt(HTTP_SECTION, null, RETRY_INTERVAL_KEY, RETRY_INTERVAL);
    assertThat(getConfiguration().http().retryInterval()).isEqualTo(RETRY_INTERVAL);

    globalPluginConfig.setString(HTTP_SECTION, null, RETRY_INTERVAL_KEY, INVALID_INT);
    assertThat(getConfiguration().http().retryInterval()).isEqualTo(DEFAULT_RETRY_INTERVAL);
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
  public void testGetDefaultSharedDirectory() throws Exception {
    assertEquals(
        getConfiguration().main().sharedDirectory(), sitePaths.resolve(DEFAULT_SHARED_DIRECTORY));
  }

  @Test
  public void testGetSharedDirectory() throws Exception {
    globalPluginConfig.setString(
        MAIN_SECTION, null, SHARED_DIRECTORY_KEY, SHARED_DIR_PATH.toString());
    assertEquals(getConfiguration().main().sharedDirectory(), SHARED_DIR_PATH);
  }

  @Test
  public void testRelativeSharedDir() {
    globalPluginConfig.setString(
        MAIN_SECTION, null, SHARED_DIRECTORY_KEY, RELATIVE_SHARED_DIRECTORY);
    assertEquals(
        getConfiguration().main().sharedDirectory(), SITE_PATH.resolve(RELATIVE_SHARED_DIRECTORY));
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
  public void testHealthCheckEnabled() throws Exception {
    assertThat(getConfiguration().healthCheck().enabled()).isEqualTo(DEFAULT_HEALTH_CHECK_ENABLED);

    globalPluginConfig.setBoolean(HEALTH_CHECK_SECTION, null, ENABLE_KEY, false);
    assertThat(getConfiguration().healthCheck().enabled()).isFalse();

    globalPluginConfig.setBoolean(HEALTH_CHECK_SECTION, null, ENABLE_KEY, true);
    assertThat(getConfiguration().healthCheck().enabled()).isTrue();
  }
}
