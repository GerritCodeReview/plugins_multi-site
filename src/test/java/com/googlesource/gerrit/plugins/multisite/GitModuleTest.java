// Copyright (C) 2022 The Android Open Source Project
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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.config.GlobalPluginConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.git.MultiBaseLocalDiskRepositoryManager;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.validation.MultiSiteGitRepositoryManager;
import org.junit.Test;

@UseLocalDisk
@NoHttpd
public class GitModuleTest extends AbstractDaemonTest {
  @Inject private GitRepositoryManager gitRepoManager;
  @Inject private LocalDiskRepositoryManager wrapped;

  @Test
  @GerritConfig(
      name = "gerrit.installDbModule",
      value = "com.googlesource.gerrit.plugins.multisite.GitModule")
  public void shouldUseLocalDiskRepositoryManagerByDefault() {
    assertThat(gitRepoManager).isInstanceOf(MultiSiteGitRepositoryManager.class);
    assertThat(wrapped).isNotInstanceOf(MultiBaseLocalDiskRepositoryManager.class);
  }

  @Test
  @GerritConfig(
      name = "gerrit.installDbModule",
      value = "com.googlesource.gerrit.plugins.multisite.GitModule")
  @GerritConfig(name = "repository.r1.basePath", value = "/tmp/git1")
  public void shouldUseMultiBaseLocalDiskRepositoryManagerWhenItIsConfigured() {
    assertThat(gitRepoManager).isInstanceOf(MultiSiteGitRepositoryManager.class);
    assertThat(wrapped).isInstanceOf(MultiBaseLocalDiskRepositoryManager.class);
  }

  @Test
  @GerritConfig(
      name = "gerrit.installDbModule",
      value = "com.googlesource.gerrit.plugins.multisite.GitModule")
  @GlobalPluginConfig(
      pluginName = Configuration.PLUGIN_NAME,
      name = "ref-database.enabled",
      value = "false")
  public void shouldInstallDefaultGerritGitManagerWhenRefDbIsDisabled() {
    assertThat(gitRepoManager).isInstanceOf(LocalDiskRepositoryManager.class);
  }
}
