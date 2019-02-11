// Copyright (C) 2018 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.forwarder.rest;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Project;
import org.junit.Test;

@NoHttpd
@TestPlugin(
    name = "multi-site",
    sysModule = "com.googlesource.gerrit.plugins.multisite.Module",
    httpModule = "com.googlesource.gerrit.plugins.multisite.HttpModule")
public class ProjectListRestApiServletIT extends LightweightPluginDaemonTest {
  private static final Project.NameKey SOME_PROJECT = new Project.NameKey("org-a/some-project");

  @Test
  @UseLocalDisk
  public void addProject() throws Exception {

    assertThat(projectCache.all()).doesNotContain(SOME_PROJECT);
    adminRestSession
        .post("/plugins/multi-site/cache/project_list/" + Url.encode(SOME_PROJECT.get()))
        .assertNoContent();
    assertThat(projectCache.all()).contains(SOME_PROJECT);
  }

  @Test
  @UseLocalDisk
  public void removeProject() throws Exception {
    addProject();
    adminRestSession
        .delete("/plugins/multi-site/cache/project_list/" + Url.encode(SOME_PROJECT.get()))
        .assertNoContent();
    assertThat(projectCache.all()).doesNotContain(SOME_PROJECT);
  }
}
