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
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectIndexEvent;
import com.googlesource.gerrit.plugins.multisite.index.ForwardedIndexExecutor;
import com.googlesource.gerrit.plugins.multisite.index.ProjectChecker;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Index a project using {@link ProjectIndexer}. This class is meant to be used on the receiving
 * side of the {@link IndexEventForwarder} since it will prevent indexed project to be forwarded
 * again causing an infinite forwarding loop between the 2 nodes. It will also make sure no
 * concurrent indexing is done for the same project name.
 */
@Singleton
public class ForwardedIndexProjectHandler
    extends ForwardedIndexingHandlerWithRetries<String, ProjectIndexEvent> {
  private final ProjectIndexer indexer;
  private final ProjectChecker projectChecker;

  @Inject
  ForwardedIndexProjectHandler(
      ProjectIndexer indexer,
      ProjectChecker projectChecker,
      OneOffRequestContext oneOffRequestContext,
      @ForwardedIndexExecutor ScheduledExecutorService indexExecutor,
      Configuration config) {
    super(indexExecutor, config, oneOffRequestContext);
    this.indexer = indexer;
    this.projectChecker = projectChecker;
  }

  @Override
  protected void doIndex(String projectName, Optional<ProjectIndexEvent> event) {
    attemptToIndex(projectName, event, 0);
  }

  @Override
  protected void reindex(String id) {
    indexer.index(Project.nameKey(id));
  }

  @Override
  protected String indexName() {
    return "project";
  }

  @Override
  protected void attemptToIndex(String id, Optional<ProjectIndexEvent> indexEvent, int retryCount) {
    reindexAndCheckIsUpToDate(id, indexEvent, projectChecker, retryCount);
  }

  @Override
  protected void doDelete(String projectName, Optional<ProjectIndexEvent> event) {
    throw new UnsupportedOperationException("Delete from project index not supported");
  }
}
