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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.reviewdb.client.Project;
import org.junit.Test;

@NoHttpd
@TestPlugin(
  name = "high-availability",
  sysModule = "com.ericsson.gerrit.plugins.highavailability.Module",
  httpModule = "com.ericsson.gerrit.plugins.highavailability.HttpModule"
)
public class ProjectListRestApiServletIT extends LightweightPluginDaemonTest {
  @Test
  @UseLocalDisk
  public void addProject() throws Exception {
    Project.NameKey newProject = new Project.NameKey("someNewProject");
    assertThat(projectCache.all()).doesNotContain(newProject);
    adminRestSession
        .post("/plugins/high-availability/cache/project_list/" + newProject.get())
        .assertNoContent();
    assertThat(projectCache.all()).contains(newProject);
  }

  @Test
  @UseLocalDisk
  public void removeProject() throws Exception {
    assertThat(projectCache.all()).contains(project);
    adminRestSession
        .delete("/plugins/high-availability/cache/project_list/" + project.get())
        .assertNoContent();
    assertThat(projectCache.all()).doesNotContain(project);
  }
}
