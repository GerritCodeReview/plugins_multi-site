// Copyright (C) 2015 Ericsson
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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class IndexChangeRestApiServlet extends AbstractIndexRestApiServlet<Change.Id> {
  private static final long serialVersionUID = -1L;
  private static final Logger logger = LoggerFactory.getLogger(IndexChangeRestApiServlet.class);

  private final ChangeIndexer indexer;
  private final SchemaFactory<ReviewDb> schemaFactory;

  @Inject
  IndexChangeRestApiServlet(ChangeIndexer indexer, SchemaFactory<ReviewDb> schemaFactory) {
    super("change", true);
    this.indexer = indexer;
    this.schemaFactory = schemaFactory;
  }

  @Override
  Change.Id parse(String id) {
    return Change.Id.parse(id);
  }

  @Override
  void index(Change.Id id, Operation operation) throws IOException, OrmException {
    switch (operation) {
      case INDEX:
        try (ReviewDb db = schemaFactory.open()) {
          Change change = db.changes().get(id);
          if (change == null) {
            indexer.delete(id);
            return;
          }
          indexer.index(db, change);
          logger.debug("Change {} successfully indexed", id);
        } catch (Exception e) {
          if (!isCausedByNoSuchChangeException(e)) {
            throw e;
          }
          logger.debug("Change {} was deleted, aborting forwarded indexing the change.", id.get());
        }
        break;
      case DELETE:
        indexer.delete(id);
        logger.debug("Change {} successfully deleted from index", id);
        break;
    }
  }

  private boolean isCausedByNoSuchChangeException(Throwable throwable) {
    while (throwable != null) {
      if (throwable instanceof NoSuchChangeException) {
        return true;
      }
      throwable = throwable.getCause();
    }
    return false;
  }
}
