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

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectListUpdateEvent;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Update project list cache. This class is meant to be used on the receiving side of the {@link
 * ProjectListUpdateForwarder} since it will prevent project list updates to be forwarded again
 * causing an infinite forwarding loop between the 2 nodes.
 */
@Singleton
public class ForwardedProjectListUpdateHandler {
  private static final Logger log =
      LoggerFactory.getLogger(ForwardedProjectListUpdateHandler.class);

  private final ProjectCache projectCache;
  private final DynamicSet<NewProjectCreatedListener> projectCreatedListeners;

  @Inject
  ForwardedProjectListUpdateHandler(
      ProjectCache projectCache, DynamicSet<NewProjectCreatedListener> projectCreatedListeners) {
    this.projectCache = projectCache;
    this.projectCreatedListeners = projectCreatedListeners;
  }

  /**
   * Update the project list.
   *
   * <p>For new project creation, fire a project-created event to force the relevant listeners to
   * handle the event. Any `NewProjectCreatedListener`s in multi-site will probably want to ignore
   * the event, however to other listeners (eg those defined in HA plugin) this is important as it
   * will forward the update to the other node, and therefore the newly-created project will be
   * visible in the other master node's project list.
   *
   * <p>For project list remove events, the update will not be forwarded to the other node.
   *
   * @param event the content of Project list update event to add or remove.
   * @throws IOException
   */
  public void update(ProjectListUpdateEvent event) throws IOException {
    Project.NameKey projectKey = Project.nameKey(event.projectName);
    try {
      Context.setForwardedEvent(true);
      if (event.remove) {
        projectCache.remove(projectKey);
        log.debug("Removed {} from project list", event.projectName);
      } else {
        projectCache.onCreateProject(projectKey);
        NewProjectCreatedListener.Event newProjectCreatedEvent =
            new NewProjectCreatedListener.Event() {
              @Override
              public String getHeadName() {
                return null;
              }

              @Override
              public String getProjectName() {
                return event.projectName;
              }

              @Override
              public NotifyHandling getNotify() {
                return null;
              }
            };
        projectCreatedListeners.forEach(l -> l.onNewProjectCreated(newProjectCreatedEvent));
        log.debug("Added {} to project list", event.projectName);
      }
    } finally {
      Context.unsetForwardedEvent();
    }
  }
}
