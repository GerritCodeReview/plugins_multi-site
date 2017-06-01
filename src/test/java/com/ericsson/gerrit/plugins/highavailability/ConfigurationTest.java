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

import static com.ericsson.gerrit.plugins.highavailability.Configuration.CACHE_THREAD_POOL_SIZE_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.CLEANUP_INTERVAL_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.CONNECTION_TIMEOUT_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.DEFAULT_CLEANUP_INTERVAL_MS;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.DEFAULT_MAX_TRIES;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.DEFAULT_RETRY_INTERVAL;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.DEFAULT_THREAD_POOL_SIZE;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.DEFAULT_TIMEOUT_MS;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.INDEX_THREAD_POOL_SIZE_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.MAX_TRIES_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.PASSWORD_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.RETRY_INTERVAL_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.SHARED_DIRECTORY_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.SOCKET_TIMEOUT_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.URL_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.USER_KEY;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.ProvisionException;
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
  private static final String ERROR_MESSAGE = "some error message";

  @Mock private PluginConfigFactory cfgFactoryMock;
  @Mock private PluginConfig configMock;
  private Configuration configuration;
  private String pluginName = "high-availability";

  @Before
  public void setUp() {
    when(cfgFactoryMock.getFromGerritConfig(pluginName, true)).thenReturn(configMock);
    when(configMock.getString(SHARED_DIRECTORY_KEY)).thenReturn(SHARED_DIRECTORY);
  }

  private void initializeConfiguration() {
    configuration = new Configuration(cfgFactoryMock, pluginName);
  }

  @Test
  public void testGetUrl() throws Exception {
    initializeConfiguration();
    assertThat(configuration.getUrl()).isEqualTo(EMPTY);

    when(configMock.getString(URL_KEY)).thenReturn(URL);
    initializeConfiguration();
    assertThat(configuration.getUrl()).isEqualTo(URL);
  }

  @Test
  public void testGetUrlIsDroppingTrailingSlash() throws Exception {
    when(configMock.getString("url")).thenReturn(URL + "/");
    initializeConfiguration();
    assertThat(configuration).isNotNull();
    assertThat(configuration.getUrl()).isEqualTo(URL);
  }

  @Test
  public void testGetUser() throws Exception {
    initializeConfiguration();
    assertThat(configuration.getUser()).isEqualTo(EMPTY);

    when(configMock.getString(USER_KEY)).thenReturn(USER);
    initializeConfiguration();
    assertThat(configuration.getUser()).isEqualTo(USER);
  }

  @Test
  public void testGetPassword() throws Exception {
    initializeConfiguration();
    assertThat(configuration.getPassword()).isEqualTo(EMPTY);

    when(configMock.getString(PASSWORD_KEY)).thenReturn(PASS);
    initializeConfiguration();
    assertThat(configuration.getPassword()).isEqualTo(PASS);
  }

  @Test
  public void testGetConnectionTimeout() throws Exception {
    initializeConfiguration();
    assertThat(configuration.getConnectionTimeout()).isEqualTo(0);

    when(configMock.getInt(CONNECTION_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS)).thenReturn(TIMEOUT);
    initializeConfiguration();
    assertThat(configuration.getConnectionTimeout()).isEqualTo(TIMEOUT);

    when(configMock.getInt(CONNECTION_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS))
        .thenThrow(new IllegalArgumentException(ERROR_MESSAGE));
    initializeConfiguration();
    assertThat(configuration.getConnectionTimeout()).isEqualTo(DEFAULT_TIMEOUT_MS);
  }

  @Test
  public void testGetSocketTimeout() throws Exception {
    initializeConfiguration();
    assertThat(configuration.getSocketTimeout()).isEqualTo(0);

    when(configMock.getInt(SOCKET_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS)).thenReturn(TIMEOUT);
    initializeConfiguration();
    assertThat(configuration.getSocketTimeout()).isEqualTo(TIMEOUT);

    when(configMock.getInt(SOCKET_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS))
        .thenThrow(new IllegalArgumentException(ERROR_MESSAGE));
    initializeConfiguration();
    assertThat(configuration.getSocketTimeout()).isEqualTo(DEFAULT_TIMEOUT_MS);
  }

  @Test
  public void testGetMaxTries() throws Exception {
    initializeConfiguration();
    assertThat(configuration.getMaxTries()).isEqualTo(0);

    when(configMock.getInt(MAX_TRIES_KEY, DEFAULT_MAX_TRIES)).thenReturn(MAX_TRIES);
    initializeConfiguration();
    assertThat(configuration.getMaxTries()).isEqualTo(MAX_TRIES);

    when(configMock.getInt(MAX_TRIES_KEY, DEFAULT_MAX_TRIES))
        .thenThrow(new IllegalArgumentException(ERROR_MESSAGE));
    initializeConfiguration();
    assertThat(configuration.getMaxTries()).isEqualTo(DEFAULT_MAX_TRIES);
  }

  @Test
  public void testGetRetryInterval() throws Exception {
    initializeConfiguration();
    assertThat(configuration.getRetryInterval()).isEqualTo(0);

    when(configMock.getInt(RETRY_INTERVAL_KEY, DEFAULT_RETRY_INTERVAL)).thenReturn(RETRY_INTERVAL);
    initializeConfiguration();
    assertThat(configuration.getRetryInterval()).isEqualTo(RETRY_INTERVAL);

    when(configMock.getInt(RETRY_INTERVAL_KEY, DEFAULT_RETRY_INTERVAL))
        .thenThrow(new IllegalArgumentException(ERROR_MESSAGE));
    initializeConfiguration();
    assertThat(configuration.getRetryInterval()).isEqualTo(DEFAULT_RETRY_INTERVAL);
  }

  @Test
  public void testGetIndexThreadPoolSize() throws Exception {
    initializeConfiguration();
    assertThat(configuration.getIndexThreadPoolSize()).isEqualTo(0);

    when(configMock.getInt(INDEX_THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE))
        .thenReturn(THREAD_POOL_SIZE);
    initializeConfiguration();
    assertThat(configuration.getIndexThreadPoolSize()).isEqualTo(THREAD_POOL_SIZE);

    when(configMock.getInt(INDEX_THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE))
        .thenThrow(new IllegalArgumentException(ERROR_MESSAGE));
    initializeConfiguration();
    assertThat(configuration.getIndexThreadPoolSize()).isEqualTo(DEFAULT_THREAD_POOL_SIZE);
  }

  @Test
  public void testGetCacheThreadPoolSize() throws Exception {
    initializeConfiguration();
    assertThat(configuration.getCacheThreadPoolSize()).isEqualTo(0);

    when(configMock.getInt(CACHE_THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE))
        .thenReturn(THREAD_POOL_SIZE);
    initializeConfiguration();
    assertThat(configuration.getCacheThreadPoolSize()).isEqualTo(THREAD_POOL_SIZE);

    when(configMock.getInt(CACHE_THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE))
        .thenThrow(new IllegalArgumentException(ERROR_MESSAGE));
    initializeConfiguration();
    assertThat(configuration.getCacheThreadPoolSize()).isEqualTo(DEFAULT_THREAD_POOL_SIZE);
  }

  @Test
  public void testGetSharedDirectory() throws Exception {
    initializeConfiguration();
    assertThat(configuration.getSharedDirectory()).isEqualTo(SHARED_DIRECTORY);
  }

  @Test(expected = ProvisionException.class)
  public void shouldThrowExceptionIfSharedDirectoryNotConfigured() throws Exception {
    when(configMock.getString(SHARED_DIRECTORY_KEY)).thenReturn(null);
    initializeConfiguration();
  }

  @Test
  public void testGetCleanupInterval() throws Exception {
    initializeConfiguration();
    assertThat(configuration.getCleanupInterval()).isEqualTo(DEFAULT_CLEANUP_INTERVAL_MS);

    when(configMock.getString(CLEANUP_INTERVAL_KEY)).thenReturn("30 seconds");
    initializeConfiguration();
    assertThat(configuration.getCleanupInterval()).isEqualTo(SECONDS.toMillis(30));
  }
}
