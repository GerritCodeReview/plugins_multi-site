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

package com.ericsson.gerrit.plugins.highavailability.forwarder;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Update project list cache. This class is meant to be used on the receiving side of the {@link
 * Forwarder} since it will prevent project list updates to be forwarded again causing an infinite
 * forwarding loop between the 2 nodes.
 */
@Singleton
public class ForwardedProjectListUpdateHandler {
  private static final Logger logger =
      LoggerFactory.getLogger(ForwardedProjectListUpdateHandler.class);

  private final ProjectCache projectCache;

  @Inject
  ForwardedProjectListUpdateHandler(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  /**
   * Update the project list, update will not be forwarded to the other node
   *
   * @param projectName the name of the project to add or remove.
   * @param remove true to remove, false to add project.
   */
  public void update(String projectName, boolean remove) {
    Project.NameKey projectKey = new Project.NameKey(projectName);
    try {
      Context.setForwardedEvent(true);
      if (remove) {
        projectCache.remove(projectKey);
        logger.debug("Removed {} from project list", projectName);
      } else {
        projectCache.onCreateProject(projectKey);
        logger.debug("Added {} to project list", projectName);
      }
    } finally {
      Context.unsetForwardedEvent();
    }
  }
}
