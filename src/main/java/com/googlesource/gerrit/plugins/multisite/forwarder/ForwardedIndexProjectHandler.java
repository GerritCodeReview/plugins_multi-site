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
import com.google.gerrit.index.project.ProjectIndexer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectIndexEvent;
import com.googlesource.gerrit.plugins.multisite.index.ForwardedIndexExecutor;
import com.googlesource.gerrit.plugins.multisite.index.ProjectChecker;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Index a project using {@link ProjectIndexer}. This class is meant to be used on the receiving
 * side of the {@link IndexEventForwarder} since it will prevent indexed project to be forwarded
 * again causing an infinite forwarding loop between the 2 nodes. It will also make sure no
 * concurrent indexing is done for the same project name.
 */
@Singleton
public class ForwardedIndexProjectHandler
    extends ForwardedIndexingHandler<String, ProjectIndexEvent> {
  private final ProjectIndexer indexer;
  private final int retryInterval;
  private final int maxTries;
  private final ProjectChecker projectChecker;
  private final ScheduledExecutorService indexExecutor;

  @Inject
  ForwardedIndexProjectHandler(
      ProjectIndexer indexer,
      ProjectChecker projectChecker,
      @ForwardedIndexExecutor ScheduledExecutorService indexExecutor,
      Configuration config) {
    super(config.index().numStripedLocks());
    this.indexer = indexer;
    Configuration.Index indexConfig = config.index();
    this.retryInterval = indexConfig != null ? indexConfig.retryInterval() : 0;
    this.maxTries = indexConfig != null ? indexConfig.maxTries() : 0;
    this.indexExecutor = indexExecutor;
    this.projectChecker = projectChecker;
  }

  @Override
  protected void doIndex(String projectName, Optional<ProjectIndexEvent> event) {
    if (!attemptIndex(projectName, event)) {
      log.warn("First Attempt failed, scheduling again after {} msecs", retryInterval);
      rescheduleIndex(projectName, event, 1);
    }
  }

  public boolean attemptIndex(String projectName, Optional<ProjectIndexEvent> event) {
    log.debug("Attempt to index project {}, event: [{}]", projectName, event);
    final Project.NameKey projectNameKey = Project.nameKey(projectName);
    if (projectChecker.isProjectUpToDate(projectNameKey)) {
      indexer.index(projectNameKey);
      log.debug("Project {} successfully indexed", projectName);
      return true;
    }
    return false;
  }

  public void rescheduleIndex(
      String projectName, Optional<ProjectIndexEvent> event, int retryCount) {
    if (retryCount > maxTries) {
      log.error(
          "Project {} could not be indexed after {} retries. index could be stale.",
          projectName,
          retryCount);

      return;
    }

    log.warn(
        "Retrying for the #{} time to index project {} after {} msecs",
        retryCount,
        projectName,
        retryInterval);

    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        indexExecutor.schedule(
            () -> {
              Context.setForwardedEvent(true);
              if (!attemptIndex(projectName, event)) {
                log.warn(
                    "Attempt {} to index project {} failed, scheduling again after {} msecs",
                    retryCount,
                    projectName,
                    retryInterval);
                rescheduleIndex(projectName, event, retryCount + 1);
              }
            },
            retryInterval,
            TimeUnit.MILLISECONDS);
  }

  @Override
  protected void doDelete(String projectName, Optional<ProjectIndexEvent> event) {
    throw new UnsupportedOperationException("Delete from project index not supported");
  }
}
