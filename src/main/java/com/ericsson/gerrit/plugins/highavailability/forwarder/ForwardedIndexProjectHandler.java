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

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.gerrit.index.project.ProjectIndexer;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

/**
 * Index a project using {@link ProjectIndexer}. This class is meant to be used on the receiving
 * side of the {@link Forwarder} since it will prevent indexed group to be forwarded again causing
 * an infinite forwarding loop between the 2 nodes. It will also make sure no concurrent indexing is
 * done for the same project name.
 */
@Singleton
public class ForwardedIndexProjectHandler extends ForwardedIndexingHandler<Project.NameKey> {
  private final ProjectIndexer indexer;

  @Inject
  ForwardedIndexProjectHandler(ProjectIndexer indexer, Configuration config) {
    super(config.index().numStripedLocks());
    this.indexer = indexer;
  }

  @Override
  protected void doIndex(Project.NameKey projectName, Optional<IndexEvent> indexEvent)
      throws IOException, OrmException {
    indexer.index(projectName);
    log.debug("Project {} successfully indexed", projectName);
  }

  @Override
  protected void doDelete(Project.NameKey projectName, Optional<IndexEvent> indexEvent) {
    throw new UnsupportedOperationException("Delete from project index not supported");
  }
}
