// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.server.config.SitePaths;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ModuleTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Configuration configMock;

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private Module module;

  @Before
  public void setup() {
    module = new Module(configMock);
  }

  @Test
  public void shouldGetInstanceId() throws Exception {
    File tmpSitePath = tempFolder.newFolder();
    File tmpPluginDataPath =
        Paths.get(tmpSitePath.getPath(), "data", Configuration.PLUGIN_NAME).toFile();
    tmpPluginDataPath.mkdirs();
    Path path = Paths.get(tmpPluginDataPath.getPath(), Configuration.INSTANCE_ID_FILE);
    SitePaths sitePaths = new SitePaths(Paths.get(tmpSitePath.getPath()));
    assertThat(path.toFile().exists()).isFalse();

    UUID gotUUID1 = module.getInstanceId(sitePaths);
    assertThat(gotUUID1).isNotNull();
    assertThat(path.toFile().exists()).isTrue();

    UUID gotUUID2 = module.getInstanceId(sitePaths);
    assertThat(gotUUID1).isEqualTo(gotUUID2);
  }
}
