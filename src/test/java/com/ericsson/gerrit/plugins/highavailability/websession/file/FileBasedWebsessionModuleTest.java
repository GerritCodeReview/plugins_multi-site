// Copyright (C) 2017 Ericsson
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

package com.ericsson.gerrit.plugins.highavailability.websession.file;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FileBasedWebsessionModuleTest {

  private static String SOME_PLUGIN_NAME = "somePluginName";

  @Mock
  private PluginConfig configMock;
  @Mock
  private PluginConfigFactory pluginCfgFactoryMock;

  private FileBasedWebsessionModule module;

  @Before
  public void setUp() throws Exception {
    when(pluginCfgFactoryMock.getFromGerritConfig(SOME_PLUGIN_NAME, true))
        .thenReturn(configMock);
    module = new FileBasedWebsessionModule();
  }

  @Test
  public void testDetCleanupIntervalDefaultValue() {
    assertThat(
        module.getCleanupInterval(pluginCfgFactoryMock, SOME_PLUGIN_NAME))
            .isEqualTo(HOURS.toMillis(24));
  }

  @Test
  public void testDetCleanupIntervalConfiguredValue() {
    when(configMock.getString("cleanupInterval")).thenReturn("30 seconds");
    assertThat(
        module.getCleanupInterval(pluginCfgFactoryMock, SOME_PLUGIN_NAME))
            .isEqualTo(SECONDS.toMillis(30));
  }
}
