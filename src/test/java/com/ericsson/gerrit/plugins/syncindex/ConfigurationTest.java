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

package com.ericsson.gerrit.plugins.syncindex;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;

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
  private static final boolean CUSTOM_VALUES = true;
  private static final boolean DEFAULT_VALUES = false;
  private static final int TIMEOUT = 5000;
  private static final int MAX_TRIES = 5;
  private static final int RETRY_INTERVAL = 1000;
  private static final int THREAD_POOL_SIZE = 1;

  @Mock
  private PluginConfigFactory cfgFactoryMock;
  @Mock
  private PluginConfig configMock;
  private Configuration configuration;
  private String pluginName = "sync-events";

  @Before
  public void setUp() throws Exception {
    when(cfgFactoryMock.getFromGerritConfig(pluginName, true))
        .thenReturn(configMock);
  }

  @Test
  public void testValuesPresentInGerritConfig() throws Exception {
    buildMocks(CUSTOM_VALUES);
    assertThat(configuration.getUrl()).isEqualTo(URL);
    assertThat(configuration.getUser()).isEqualTo(USER);
    assertThat(configuration.getPassword()).isEqualTo(PASS);
    assertThat(configuration.getConnectionTimeout()).isEqualTo(TIMEOUT);
    assertThat(configuration.getSocketTimeout()).isEqualTo(TIMEOUT);
    assertThat(configuration.getMaxTries()).isEqualTo(MAX_TRIES);
    assertThat(configuration.getRetryInterval()).isEqualTo(RETRY_INTERVAL);
    assertThat(configuration.getThreadPoolSize()).isEqualTo(THREAD_POOL_SIZE);
  }

  @Test
  public void testValuesNotPresentInGerritConfig() throws Exception {
    buildMocks(DEFAULT_VALUES);
    assertThat(configuration.getUrl()).isEqualTo(EMPTY);
    assertThat(configuration.getUser()).isEqualTo(EMPTY);
    assertThat(configuration.getPassword()).isEqualTo(EMPTY);
    assertThat(configuration.getConnectionTimeout()).isEqualTo(0);
    assertThat(configuration.getSocketTimeout()).isEqualTo(0);
    assertThat(configuration.getMaxTries()).isEqualTo(0);
    assertThat(configuration.getRetryInterval()).isEqualTo(0);
    assertThat(configuration.getThreadPoolSize()).isEqualTo(0);
  }

  @Test
  public void testUrlTrailingSlashIsDropped() throws Exception {
    when(configMock.getString("url")).thenReturn(URL + "/");

    configuration = new Configuration(cfgFactoryMock, pluginName);
    assertThat(configuration).isNotNull();
    assertThat(configuration.getUrl()).isEqualTo(URL);
  }

  private void buildMocks(boolean values) {
    when(configMock.getString("url")).thenReturn(values ? URL : null);
    when(configMock.getString("user")).thenReturn(values ? USER : null);
    when(configMock.getString("password")).thenReturn(values ? PASS : null);
    when(configMock.getInt("connectionTimeout", TIMEOUT))
        .thenReturn(values ? TIMEOUT : 0);
    when(configMock.getInt("socketTimeout", TIMEOUT))
        .thenReturn(values ? TIMEOUT : 0);
    when(configMock.getInt("maxTries", MAX_TRIES))
        .thenReturn(values ? MAX_TRIES : 0);
    when(configMock.getInt("retryInterval", RETRY_INTERVAL))
        .thenReturn(values ? RETRY_INTERVAL : 0);
    when(configMock.getInt("threadPoolSize", THREAD_POOL_SIZE))
        .thenReturn(values ? THREAD_POOL_SIZE : 0);

    configuration = new Configuration(cfgFactoryMock, pluginName);
  }
}
