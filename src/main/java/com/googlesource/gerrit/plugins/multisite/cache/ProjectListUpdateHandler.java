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

package com.googlesource.gerrit.plugins.multisite.cache;

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.events.ProjectEvent;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.forwarder.Context;
import com.googlesource.gerrit.plugins.multisite.forwarder.Forwarder;
import java.util.concurrent.Executor;

@Singleton
public class ProjectListUpdateHandler implements NewProjectCreatedListener, ProjectDeletedListener {

  private final DynamicSet<Forwarder> forwarders;
  private final Executor executor;
  private final String pluginName;

  @Inject
  public ProjectListUpdateHandler(
      DynamicSet<Forwarder> forwarders,
      @CacheExecutor Executor executor,
      @PluginName String pluginName) {
    this.forwarders = forwarders;
    this.executor = executor;
    this.pluginName = pluginName;
  }

  @Override
  public void onNewProjectCreated(
      com.google.gerrit.extensions.events.NewProjectCreatedListener.Event event) {
    process(event, false);
  }

  @Override
  public void onProjectDeleted(
      com.google.gerrit.extensions.events.ProjectDeletedListener.Event event) {
    process(event, true);
  }

  private void process(ProjectEvent event, boolean delete) {
    if (!Context.isForwardedEvent()) {
      executor.execute(new ProjectListUpdateTask(event.getProjectName(), delete));
    }
  }

  class ProjectListUpdateTask implements Runnable {
    private final String projectName;
    private final boolean delete;

    ProjectListUpdateTask(String projectName, boolean delete) {
      this.projectName = projectName;
      this.delete = delete;
    }

    @Override
    public void run() {
      if (delete) {
        forwarders.forEach(f -> f.removeFromProjectList(projectName));
      } else {
        forwarders.forEach(f -> f.addToProjectList(projectName));
      }
    }

    @Override
    public String toString() {
      return String.format(
          "[%s] Update project list in target instance: %s '%s'",
          pluginName, delete ? "remove" : "add", projectName);
    }
  }
}
