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

import com.google.common.base.Splitter;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeFinder;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

/**
 * Index a change using {@link ChangeIndexer}. This class is meant to be used on the receiving side
 * of the {@link Forwarder} since it will prevent indexed change to be forwarded again causing an
 * infinite forwarding loop between the 2 nodes. It will also make sure no concurrent indexing is
 * done for the same change id
 */
@Singleton
public class ForwardedIndexChangeHandler extends ForwardedIndexingHandler<String> {
  private final ChangeIndexer indexer;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final ChangeFinder changeFinder;

  @Inject
  ForwardedIndexChangeHandler(
      ChangeIndexer indexer, SchemaFactory<ReviewDb> schemaFactory, ChangeFinder changeFinder) {
    this.indexer = indexer;
    this.schemaFactory = schemaFactory;
    this.changeFinder = changeFinder;
  }

  @Override
  protected void doIndex(String id, Optional<IndexEvent> indexEvent)
      throws IOException, OrmException {
    ChangeNotes change = null;
    try (ReviewDb db = schemaFactory.open()) {
      change = changeFinder.findOne(id);
      if (change != null) {
        change.reload();
        indexer.index(db, change.getChange());
        log.debug("Change {} successfully indexed", id);
      }
    } catch (Exception e) {
      if (!isCausedByNoSuchChangeException(e)) {
        throw e;
      }
      log.debug("Change {} was deleted, aborting forwarded indexing the change.", id);
    }
    if (change == null) {
      indexer.delete(parseChangeId(id));
      log.debug("Change {} not found, deleted from index", id);
    }
  }

  @Override
  protected void doDelete(String id, Optional<IndexEvent> indexEvent) throws IOException {
    indexer.delete(parseChangeId(id));
    log.debug("Change {} successfully deleted from index", id);
  }

  private static Change.Id parseChangeId(String id) {
    Change.Id changeId = new Change.Id(Integer.parseInt(Splitter.on("~").splitToList(id).get(1)));
    return changeId;
  }

  private static boolean isCausedByNoSuchChangeException(Throwable throwable) {
    while (throwable != null) {
      if (throwable instanceof NoSuchChangeException) {
        return true;
      }
      throwable = throwable.getCause();
    }
    return false;
  }
}
