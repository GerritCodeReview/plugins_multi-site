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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangeReindexRunnable extends ReindexRunnable<Change> {
  private static final Logger log = LoggerFactory.getLogger(ChangeReindexRunnable.class);

  private final ForwardedIndexChangeHandler changeIdx;

  @Inject
  public ChangeReindexRunnable(
      ForwardedIndexChangeHandler changeIdx, IndexTs indexTs, OneOffRequestContext ctx) {
    super(AbstractIndexRestApiServlet.IndexName.CHANGE, indexTs, ctx);
    this.changeIdx = changeIdx;
  }

  @Override
  protected ResultSet<Change> fetchItems(ReviewDb db) throws OrmException {
    return db.changes().all();
  }

  @Override
  protected Optional<Timestamp> indexIfNeeded(ReviewDb db, Change c, Timestamp sinceTs) {
    try {
      Timestamp changeTs = c.getLastUpdatedOn();
      if (changeTs.after(sinceTs)) {
        log.info(
            "Index {}/{}/{} was updated after {}", c.getProject(), c.getId(), changeTs, sinceTs);
        changeIdx.index(c.getId(), Operation.INDEX);
        return Optional.of(changeTs);
      }
    } catch (OrmException | IOException e) {
      log.error("Reindex failed", e);
    }
    return Optional.empty();
  }
}
