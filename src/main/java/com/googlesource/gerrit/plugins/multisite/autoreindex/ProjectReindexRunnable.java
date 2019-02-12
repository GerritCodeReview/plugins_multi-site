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

package com.googlesource.gerrit.plugins.multisite.autoreindex;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.forwarder.rest.AbstractIndexRestApiServlet;
import java.sql.Timestamp;
import java.util.Optional;

public class ProjectReindexRunnable extends ReindexRunnable<Project.NameKey> {

  private final ProjectCache projectCache;

  @Inject
  public ProjectReindexRunnable(
      IndexTs indexTs, OneOffRequestContext ctx, ProjectCache projectCache) {
    super(AbstractIndexRestApiServlet.IndexName.PROJECT, indexTs, ctx);
    this.projectCache = projectCache;
  }

  @Override
  protected Iterable<Project.NameKey> fetchItems() {
    return projectCache.all();
  }

  @Override
  protected Optional<Timestamp> indexIfNeeded(Project.NameKey g, Timestamp sinceTs) {
    return Optional.empty();
  }
}
