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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

/**
 * Index a change using {@link ChangeIndexer}. This class is meant to be used on the receiving side
 * of the {@link Forwarder} since it will prevent indexed change to be forwarded again causing an
 * infinite forwarding loop between the 2 nodes. It will also make sure no concurrent indexing is
 * done for the same change id
 */
@Singleton
public class ForwardedIndexChangeHandler extends ForwardedIndexingHandler<Change.Id> {
  private final ChangeIndexer indexer;
  private final SchemaFactory<ReviewDb> schemaFactory;

  @Inject
  ForwardedIndexChangeHandler(
      ChangeIndexer indexer, SchemaFactory<ReviewDb> schemaFactory, Configuration config) {
    super(config.index().numStripedLocks());
    this.indexer = indexer;
    this.schemaFactory = schemaFactory;
  }

  @Override
  protected void doIndex(Change.Id id) throws IOException, OrmException {
    Change change = null;
    try (ReviewDb db = schemaFactory.open()) {
      change = db.changes().get(id);
      if (change != null) {
        indexer.index(db, change);
        log.debug("Change {} successfully indexed", id);
      }
    } catch (Exception e) {
      if (!isCausedByNoSuchChangeException(e)) {
        throw e;
      }
      log.debug("Change {} was deleted, aborting forwarded indexing the change.", id.get());
    }
    if (change == null) {
      indexer.delete(id);
      log.debug("Change {} not found, deleted from index", id);
    }
  }

  @Override
  protected void doDelete(Id id) throws IOException {
    indexer.delete(id);
    log.debug("Change {} successfully deleted from index", id);
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
