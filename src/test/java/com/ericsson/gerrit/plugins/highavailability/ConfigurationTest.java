// Copyright (C) 2015 Ericsson
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

import static com.ericsson.gerrit.plugins.highavailability.Configuration.CACHE_SECTION;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.CLEANUP_INTERVAL_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.CLUSTER_NAME_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.CONNECTION_TIMEOUT_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.DEFAULT_CLEANUP_INTERVAL_MS;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.DEFAULT_CLUSTER_NAME;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.DEFAULT_MAX_TRIES;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.DEFAULT_PEER_INFO_STRATEGY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.DEFAULT_RETRY_INTERVAL;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.DEFAULT_SKIP_INTERFACE_LIST;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.DEFAULT_SYNCHRONIZE;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.DEFAULT_THREAD_POOL_SIZE;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.DEFAULT_TIMEOUT_MS;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.EVENT_SECTION;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.HTTP_SECTION;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.INDEX_SECTION;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.JGROUPS_SUBSECTION;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.MAIN_SECTION;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.MAX_TRIES_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.PASSWORD_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.PEER_INFO_SECTION;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.RETRY_INTERVAL_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.SHARED_DIRECTORY_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.SKIP_INTERFACE_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.SOCKET_TIMEOUT_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.STATIC_SUBSECTION;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.STRATEGY_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.SYNCHRONIZE_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.THREAD_POOL_SIZE_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.URL_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.USER_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.WEBSESSION_SECTION;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.ProvisionException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConfigurationTest {
  private static final String PASS = "fakePass";
  private static final String USER = "fakeUser";
  private static final String URL = "fakeUrl";
  private static final String EMPTY = "";
  private static final int TIMEOUT = 5000;
  private static final int MAX_TRIES = 5;
  private static final int RETRY_INTERVAL = 1000;
  private static final int THREAD_POOL_SIZE = 1;
  private static final String SHARED_DIRECTORY = "/some/directory";
  private static final Path SHARED_DIR_PATH = Paths.get(SHARED_DIRECTORY);
  private static final String RELATIVE_SHARED_DIRECTORY = "relative/dir";
  private static final Path SITE_PATH = Paths.get("/site_path");
  private static final String ERROR_MESSAGE = "some error message";

  @Mock private PluginConfigFactory cfgFactoryMock;
  @Mock private Config configMock;
  private SitePaths site;
  private Configuration configuration;
  private String pluginName = "high-availability";

  @Before
  public void setUp() throws IOException {
    when(cfgFactoryMock.getGlobalPluginConfig(pluginName)).thenReturn(configMock);
    when(configMock.getString(MAIN_SECTION, null, SHARED_DIRECTORY_KEY))
        .thenReturn(SHARED_DIRECTORY);
    when(configMock.getEnum(PEER_INFO_SECTION, null, STRATEGY_KEY, DEFAULT_PEER_INFO_STRATEGY))
        .thenReturn(DEFAULT_PEER_INFO_STRATEGY);
    site = new SitePaths(SITE_PATH);
  }

  private void initializeConfiguration() {
    configuration = new Configuration(cfgFactoryMock, pluginName, site);
  }

  @Test
  public void testGetPeerInfoStrategy() {
    initializeConfiguration();
    assertThat(configuration.peerInfo().strategy()).isSameAs(DEFAULT_PEER_INFO_STRATEGY);

    when(configMock.getEnum(PEER_INFO_SECTION, null, STRATEGY_KEY, DEFAULT_PEER_INFO_STRATEGY))
        .thenReturn(Configuration.PeerInfoStrategy.JGROUPS);
    initializeConfiguration();
    assertThat(configuration.peerInfo().strategy())
        .isSameAs(Configuration.PeerInfoStrategy.JGROUPS);
  }

  @Test
  public void testGetUrl() throws Exception {
    initializeConfiguration();
    assertThat(configuration.peerInfoStatic().url()).isEqualTo(EMPTY);

    when(configMock.getString(PEER_INFO_SECTION, STATIC_SUBSECTION, URL_KEY)).thenReturn(URL);
    initializeConfiguration();
    assertThat(configuration.peerInfoStatic().url()).isEqualTo(URL);
  }

  @Test
  public void testGetUrlIsDroppingTrailingSlash() throws Exception {
    when(configMock.getString(PEER_INFO_SECTION, STATIC_SUBSECTION, URL_KEY)).thenReturn(URL + "/");
    initializeConfiguration();
    assertThat(configuration).isNotNull();
    assertThat(configuration.peerInfoStatic().url()).isEqualTo(URL);
  }

  @Test
  public void testJGroupsPeerInfoNullWhenStaticPeerInfoConfig() throws Exception {
    initializeConfiguration();
    assertThat(configuration.peerInfoJGroups()).isNull();
  }

  @Test
  public void testGetJGroupsChannel() throws Exception {
    when(configMock.getEnum(PEER_INFO_SECTION, null, STRATEGY_KEY, DEFAULT_PEER_INFO_STRATEGY))
        .thenReturn(Configuration.PeerInfoStrategy.JGROUPS);
    initializeConfiguration();
    assertThat(configuration.peerInfoJGroups().clusterName()).isEqualTo(DEFAULT_CLUSTER_NAME);

    when(configMock.getString(PEER_INFO_SECTION, JGROUPS_SUBSECTION, CLUSTER_NAME_KEY))
        .thenReturn("foo");
    initializeConfiguration();
    assertThat(configuration.peerInfoJGroups().clusterName()).isEqualTo("foo");
  }

  @Test
  public void testGetJGroupsSkipInterface() throws Exception {
    when(configMock.getEnum(PEER_INFO_SECTION, null, STRATEGY_KEY, DEFAULT_PEER_INFO_STRATEGY))
        .thenReturn(Configuration.PeerInfoStrategy.JGROUPS);
    initializeConfiguration();
    assertThat(configuration.peerInfoJGroups().skipInterface())
        .isEqualTo(DEFAULT_SKIP_INTERFACE_LIST);

    when(configMock.getStringList(PEER_INFO_SECTION, JGROUPS_SUBSECTION, SKIP_INTERFACE_KEY))
        .thenReturn(new String[] {"lo*", "eth0"});
    initializeConfiguration();
    assertThat(configuration.peerInfoJGroups().skipInterface())
        .containsAllOf("lo*", "eth0")
        .inOrder();
  }

  @Test
  public void testGetUser() throws Exception {
    initializeConfiguration();
    assertThat(configuration.http().user()).isEqualTo(EMPTY);

    when(configMock.getString(HTTP_SECTION, null, USER_KEY)).thenReturn(USER);
    initializeConfiguration();
    assertThat(configuration.http().user()).isEqualTo(USER);
  }

  @Test
  public void testGetPassword() throws Exception {
    initializeConfiguration();
    assertThat(configuration.http().password()).isEqualTo(EMPTY);

    when(configMock.getString(HTTP_SECTION, null, PASSWORD_KEY)).thenReturn(PASS);
    initializeConfiguration();
    assertThat(configuration.http().password()).isEqualTo(PASS);
  }

  @Test
  public void testGetConnectionTimeout() throws Exception {
    initializeConfiguration();
    assertThat(configuration.http().connectionTimeout()).isEqualTo(0);

    when(configMock.getInt(HTTP_SECTION, CONNECTION_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS))
        .thenReturn(TIMEOUT);
    initializeConfiguration();
    assertThat(configuration.http().connectionTimeout()).isEqualTo(TIMEOUT);

    when(configMock.getInt(HTTP_SECTION, CONNECTION_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS))
        .thenThrow(new IllegalArgumentException(ERROR_MESSAGE));
    initializeConfiguration();
    assertThat(configuration.http().connectionTimeout()).isEqualTo(DEFAULT_TIMEOUT_MS);
  }

  @Test
  public void testGetSocketTimeout() throws Exception {
    initializeConfiguration();
    assertThat(configuration.http().socketTimeout()).isEqualTo(0);

    when(configMock.getInt(HTTP_SECTION, SOCKET_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS))
        .thenReturn(TIMEOUT);
    initializeConfiguration();
    assertThat(configuration.http().socketTimeout()).isEqualTo(TIMEOUT);

    when(configMock.getInt(HTTP_SECTION, SOCKET_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS))
        .thenThrow(new IllegalArgumentException(ERROR_MESSAGE));
    initializeConfiguration();
    assertThat(configuration.http().socketTimeout()).isEqualTo(DEFAULT_TIMEOUT_MS);
  }

  @Test
  public void testGetMaxTries() throws Exception {
    initializeConfiguration();
    assertThat(configuration.http().maxTries()).isEqualTo(0);

    when(configMock.getInt(HTTP_SECTION, MAX_TRIES_KEY, DEFAULT_MAX_TRIES)).thenReturn(MAX_TRIES);
    initializeConfiguration();
    assertThat(configuration.http().maxTries()).isEqualTo(MAX_TRIES);

    when(configMock.getInt(HTTP_SECTION, MAX_TRIES_KEY, DEFAULT_MAX_TRIES))
        .thenThrow(new IllegalArgumentException(ERROR_MESSAGE));
    initializeConfiguration();
    assertThat(configuration.http().maxTries()).isEqualTo(DEFAULT_MAX_TRIES);
  }

  @Test
  public void testGetRetryInterval() throws Exception {
    initializeConfiguration();
    assertThat(configuration.http().retryInterval()).isEqualTo(0);

    when(configMock.getInt(HTTP_SECTION, RETRY_INTERVAL_KEY, DEFAULT_RETRY_INTERVAL))
        .thenReturn(RETRY_INTERVAL);
    initializeConfiguration();
    assertThat(configuration.http().retryInterval()).isEqualTo(RETRY_INTERVAL);

    when(configMock.getInt(HTTP_SECTION, RETRY_INTERVAL_KEY, DEFAULT_RETRY_INTERVAL))
        .thenThrow(new IllegalArgumentException(ERROR_MESSAGE));
    initializeConfiguration();
    assertThat(configuration.http().retryInterval()).isEqualTo(DEFAULT_RETRY_INTERVAL);
  }

  @Test
  public void testGetIndexThreadPoolSize() throws Exception {
    initializeConfiguration();
    assertThat(configuration.index().threadPoolSize()).isEqualTo(0);

    when(configMock.getInt(INDEX_SECTION, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE))
        .thenReturn(THREAD_POOL_SIZE);
    initializeConfiguration();
    assertThat(configuration.index().threadPoolSize()).isEqualTo(THREAD_POOL_SIZE);

    when(configMock.getInt(INDEX_SECTION, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE))
        .thenThrow(new IllegalArgumentException(ERROR_MESSAGE));
    initializeConfiguration();
    assertThat(configuration.index().threadPoolSize()).isEqualTo(DEFAULT_THREAD_POOL_SIZE);
  }

  @Test
  public void testGetIndexSynchronize() throws Exception {
    when(configMock.getBoolean(INDEX_SECTION, SYNCHRONIZE_KEY, DEFAULT_SYNCHRONIZE))
        .thenReturn(true);
    initializeConfiguration();
    assertThat(configuration.index().synchronize()).isTrue();

    when(configMock.getBoolean(INDEX_SECTION, SYNCHRONIZE_KEY, DEFAULT_SYNCHRONIZE))
        .thenReturn(false);
    initializeConfiguration();
    assertThat(configuration.index().synchronize()).isFalse();

    when(configMock.getBoolean(INDEX_SECTION, SYNCHRONIZE_KEY, DEFAULT_SYNCHRONIZE))
        .thenThrow(new IllegalArgumentException(ERROR_MESSAGE));
    initializeConfiguration();
    assertThat(configuration.index().synchronize()).isTrue();
  }

  @Test
  public void testGetCacheThreadPoolSize() throws Exception {
    initializeConfiguration();
    assertThat(configuration.cache().threadPoolSize()).isEqualTo(0);

    when(configMock.getInt(CACHE_SECTION, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE))
        .thenReturn(THREAD_POOL_SIZE);
    initializeConfiguration();
    assertThat(configuration.cache().threadPoolSize()).isEqualTo(THREAD_POOL_SIZE);

    when(configMock.getInt(CACHE_SECTION, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE))
        .thenThrow(new IllegalArgumentException(ERROR_MESSAGE));
    initializeConfiguration();
    assertThat(configuration.cache().threadPoolSize()).isEqualTo(DEFAULT_THREAD_POOL_SIZE);
  }

  @Test
  public void testGetCacheSynchronize() throws Exception {
    when(configMock.getBoolean(CACHE_SECTION, SYNCHRONIZE_KEY, DEFAULT_SYNCHRONIZE))
        .thenReturn(true);
    initializeConfiguration();
    assertThat(configuration.cache().synchronize()).isTrue();

    when(configMock.getBoolean(CACHE_SECTION, SYNCHRONIZE_KEY, DEFAULT_SYNCHRONIZE))
        .thenReturn(false);
    initializeConfiguration();
    assertThat(configuration.cache().synchronize()).isFalse();

    when(configMock.getBoolean(CACHE_SECTION, SYNCHRONIZE_KEY, DEFAULT_SYNCHRONIZE))
        .thenThrow(new IllegalArgumentException(ERROR_MESSAGE));
    initializeConfiguration();
    assertThat(configuration.cache().synchronize()).isTrue();
  }

  @Test
  public void testGetEventSynchronize() throws Exception {
    when(configMock.getBoolean(EVENT_SECTION, SYNCHRONIZE_KEY, DEFAULT_SYNCHRONIZE))
        .thenReturn(true);
    initializeConfiguration();
    assertThat(configuration.event().synchronize()).isTrue();

    when(configMock.getBoolean(EVENT_SECTION, SYNCHRONIZE_KEY, DEFAULT_SYNCHRONIZE))
        .thenReturn(false);
    initializeConfiguration();
    assertThat(configuration.event().synchronize()).isFalse();

    when(configMock.getBoolean(EVENT_SECTION, SYNCHRONIZE_KEY, DEFAULT_SYNCHRONIZE))
        .thenThrow(new IllegalArgumentException(ERROR_MESSAGE));
    initializeConfiguration();
    assertThat(configuration.event().synchronize()).isTrue();
  }

  @Test
  public void testGetSharedDirectory() throws Exception {
    initializeConfiguration();
    assertEquals(configuration.main().sharedDirectory(), SHARED_DIR_PATH);
  }

  @Test(expected = ProvisionException.class)
  public void shouldThrowExceptionIfSharedDirectoryNotConfigured() throws Exception {
    when(configMock.getString(MAIN_SECTION, null, SHARED_DIRECTORY_KEY)).thenReturn(null);
    initializeConfiguration();
  }

  @Test
  public void testRelativeSharedDir() {
    when(configMock.getString(MAIN_SECTION, null, SHARED_DIRECTORY_KEY))
        .thenReturn(RELATIVE_SHARED_DIRECTORY);
    initializeConfiguration();

    assertEquals(
        configuration.main().sharedDirectory(), SITE_PATH.resolve(RELATIVE_SHARED_DIRECTORY));
  }

  @Test
  public void testGetCleanupInterval() throws Exception {
    initializeConfiguration();
    assertThat(configuration.websession().cleanupInterval()).isEqualTo(DEFAULT_CLEANUP_INTERVAL_MS);

    when(configMock.getString(WEBSESSION_SECTION, null, CLEANUP_INTERVAL_KEY))
        .thenReturn("30 seconds");
    initializeConfiguration();
    assertThat(configuration.websession().cleanupInterval()).isEqualTo(SECONDS.toMillis(30));
  }

  @Test
  public void testGetWebsessionSynchronize() throws Exception {
    when(configMock.getBoolean(WEBSESSION_SECTION, SYNCHRONIZE_KEY, DEFAULT_SYNCHRONIZE))
        .thenReturn(true);
    initializeConfiguration();
    assertThat(configuration.websession().synchronize()).isTrue();

    when(configMock.getBoolean(WEBSESSION_SECTION, SYNCHRONIZE_KEY, DEFAULT_SYNCHRONIZE))
        .thenReturn(false);
    initializeConfiguration();
    assertThat(configuration.websession().synchronize()).isFalse();

    when(configMock.getBoolean(WEBSESSION_SECTION, SYNCHRONIZE_KEY, DEFAULT_SYNCHRONIZE))
        .thenThrow(new IllegalArgumentException(ERROR_MESSAGE));
    initializeConfiguration();
    assertThat(configuration.websession().synchronize()).isTrue();
  }
}
