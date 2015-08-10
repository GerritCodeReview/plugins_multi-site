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
import static org.easymock.EasyMock.expect;

import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;

public class ConfigurationTest extends EasyMockSupport {
  private static final String PASS = "fakePass";
  private static final String USER = "fakeUser";
  private static final String URL = "fakeUrl";
  private static final String EMPTY = "";
  private static final int TIMEOUT = 5000;
  private static final int MAX_TRIES = 5;
  private static final int RETRY_INTERVAL = 1000;
  private static final int THREAD_POOL_SIZE = 1;

  private PluginConfigFactory cfgFactoryMock;
  private PluginConfig configMock;
  private Configuration configuration;
  private String pluginName = "sync-index";

  @Before
  public void setUp() throws Exception {
    configMock = createNiceMock(PluginConfig.class);
    cfgFactoryMock = createMock(PluginConfigFactory.class);
    expect(cfgFactoryMock.getFromGerritConfig(pluginName, true))
        .andStubReturn(configMock);
  }

  @Test
  public void testValuesPresentInGerritConfig() throws Exception {
    buildMocks(true);
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
    buildMocks(false);
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
    expect(configMock.getString("url")).andReturn(URL + "/");
    replayAll();
    configuration = new Configuration(cfgFactoryMock, pluginName);
    assertThat(configuration).isNotNull();
    assertThat(configuration.getUrl()).isEqualTo(URL);
  }

  private void buildMocks(boolean values) {
    expect(configMock.getString("url")).andReturn(values ? URL : null);
    expect(configMock.getString("user")).andReturn(values ? USER : null);
    expect(configMock.getString("password")).andReturn(values ? PASS : null);
    expect(configMock.getInt("connectionTimeout", TIMEOUT))
        .andReturn(values ? TIMEOUT : 0);
    expect(configMock.getInt("socketTimeout", TIMEOUT))
        .andReturn(values ? TIMEOUT : 0);
    expect(configMock.getInt("maxTries", MAX_TRIES))
        .andReturn(values ? MAX_TRIES : 0);
    expect(configMock.getInt("retryInterval", RETRY_INTERVAL))
        .andReturn(values ? RETRY_INTERVAL : 0);
    expect(configMock.getInt("threadPoolSize", THREAD_POOL_SIZE))
        .andReturn(values ? THREAD_POOL_SIZE : 0);
    replayAll();
    configuration = new Configuration(cfgFactoryMock, pluginName);
  }
}
