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

package com.ericsson.gerrit.plugins.highavailability;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.ProvisionException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ModuleTest {

  private static String PLUGIN_NAME = "somePluginName";

  @Mock private PluginConfigFactory pluginConfigFactoryMock;

  @Mock private PluginConfig pluginConfigMock;

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private Module module;

  @Before
  public void setUp() {
    when(pluginConfigFactoryMock.getFromGerritConfig(PLUGIN_NAME, true))
        .thenReturn(pluginConfigMock);
    module = new Module();
  }

  @Test(expected = ProvisionException.class)
  public void shouldThrowExceptionIfSharedDirectoryNotConfigured() throws IOException {
    module.getSharedDirectory(pluginConfigFactoryMock, PLUGIN_NAME);
  }

  @Test
  public void shouldReturnConfiguredSharedDirectory() throws IOException {
    File configuredDirectory = tempFolder.newFolder();
    when(pluginConfigMock.getString("sharedDirectory"))
        .thenReturn(configuredDirectory.getAbsolutePath());

    Path sharedDirectory = module.getSharedDirectory(pluginConfigFactoryMock, PLUGIN_NAME);
    assertThat(sharedDirectory.toString()).isEqualTo(configuredDirectory.toString());
  }

  @Test
  public void shouldCreateSharedDirectoryIfItDoesNotExist() throws IOException {
    File configuredDirectory = tempFolder.newFolder();
    assertThat(configuredDirectory.delete()).isTrue();
    when(pluginConfigMock.getString("sharedDirectory"))
        .thenReturn(configuredDirectory.getAbsolutePath());

    Path sharedDirectory = module.getSharedDirectory(pluginConfigFactoryMock, PLUGIN_NAME);
    assertThat(sharedDirectory.toFile().exists()).isTrue();
  }

  @Test(expected = IOException.class)
  public void shouldThrowAnExceptionIfAnErrorOccurCreatingSharedDirectory() throws IOException {
    File configuredDirectory = tempFolder.newFile();
    when(pluginConfigMock.getString("sharedDirectory"))
        .thenReturn(configuredDirectory.getAbsolutePath());

    module.getSharedDirectory(pluginConfigFactoryMock, PLUGIN_NAME);
  }
}
