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

package com.ericsson.gerrit.plugins.highavailability.autoreindex;

import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexChangeHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.AbstractIndexRestApiServlet;
import com.google.common.collect.Streams;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ChangeNotesResult;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangeReindexRunnable extends ReindexRunnable<Change> {
  private static final Logger log = LoggerFactory.getLogger(ChangeReindexRunnable.class);

  private final ForwardedIndexChangeHandler changeIdx;

  private final ProjectCache projectCache;

  private final GitRepositoryManager repoManager;

  private final ChangeNotes.Factory notesFactory;

  private static class StreamIterable implements Iterable<Change> {

    private final Stream<Change> stream;

    public StreamIterable(Stream<Change> stream) {
      this.stream = stream;
    }

    @Override
    public Iterator<Change> iterator() {
      return stream.iterator();
    }
  }

  @Inject
  public ChangeReindexRunnable(
      ForwardedIndexChangeHandler changeIdx,
      IndexTs indexTs,
      OneOffRequestContext ctx,
      ProjectCache projectCache,
      GitRepositoryManager repoManager,
      ChangeNotes.Factory notesFactory) {
    super(AbstractIndexRestApiServlet.IndexName.CHANGE, indexTs, ctx);
    this.changeIdx = changeIdx;
    this.projectCache = projectCache;
    this.repoManager = repoManager;
    this.notesFactory = notesFactory;
  }

  @Override
  protected Iterable<Change> fetchItems(ReviewDb db) throws Exception {
    Stream<Change> allChangesStream = Stream.empty();
    Iterable<Project.NameKey> projects = projectCache.all();
    for (Project.NameKey projectName : projects) {
      try (Repository repo = repoManager.openRepository(projectName)) {
        Stream<Change> projectChangesStream =
            notesFactory
                .scan(repo, db, projectName)
                .map(
                    (ChangeNotesResult changeNotes) -> {
                      return changeNotes.notes().getChange();
                    });
        allChangesStream = Streams.concat(allChangesStream, projectChangesStream);
      }
    }
    return new StreamIterable(allChangesStream);
  }

  @Override
  protected Optional<Timestamp> indexIfNeeded(ReviewDb db, Change c, Timestamp sinceTs) {
    try {
      Timestamp changeTs = c.getLastUpdatedOn();
      if (changeTs.after(sinceTs)) {
        log.info(
            "Index {}/{}/{} was updated after {}", c.getProject(), c.getId(), changeTs, sinceTs);
        changeIdx.index(c.getProject() + "~" + c.getId(), Operation.INDEX, Optional.empty());
        return Optional.of(changeTs);
      }
    } catch (OrmException | IOException e) {
      log.error("Reindex failed", e);
    }
    return Optional.empty();
  }
}
