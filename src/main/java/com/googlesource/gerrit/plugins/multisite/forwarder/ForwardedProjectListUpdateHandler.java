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

package com.googlesource.gerrit.plugins.multisite.forwarder;

import static com.googlesource.gerrit.plugins.multisite.MultiSiteLogFile.multisiteLog;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectListUpdateEvent;
import java.io.IOException;

/**
 * Update project list cache. This class is meant to be used on the receiving side of the {@link
 * ProjectListUpdateForwarder} since it will prevent project list updates to be forwarded again
 * causing an infinite forwarding loop between the 2 nodes.
 */
@Singleton
public class ForwardedProjectListUpdateHandler {

  private final ProjectCache projectCache;

  @Inject
  ForwardedProjectListUpdateHandler(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  /**
   * Update the project list, update will not be forwarded to the other node
   *
   * @param event the content of Project liste update event to add or remove.
   * @throws IOException
   */
  public void update(ProjectListUpdateEvent event) throws IOException {
    Project.NameKey projectKey = new Project.NameKey(event.projectName);
    try {
      Context.setForwardedEvent(true);
      if (event.remove) {
        projectCache.remove(projectKey);
        multisiteLog.debug("Removed {} from project list", event.projectName);
      } else {
        projectCache.onCreateProject(projectKey);
        multisiteLog.debug("Added {} to project list", event.projectName);
      }
    } finally {
      Context.unsetForwardedEvent();
    }
  }
}
