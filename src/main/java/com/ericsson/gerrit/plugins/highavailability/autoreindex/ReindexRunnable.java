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

import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.AbstractIndexRestApiServlet;
import com.google.common.base.Stopwatch;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class ReindexRunnable<T> implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(ReindexRunnable.class);

  private final AbstractIndexRestApiServlet.IndexName itemName;
  private final OneOffRequestContext ctx;
  private final IndexTs indexTs;
  private Timestamp newLastIndexTs;

  @Inject
  public ReindexRunnable(
      AbstractIndexRestApiServlet.IndexName itemName, IndexTs indexTs, OneOffRequestContext ctx) {
    this.itemName = itemName;
    this.ctx = ctx;
    this.indexTs = indexTs;
  }

  @Override
  public void run() {
    Optional<LocalDateTime> maybeIndexTs = indexTs.getUpdateTs(itemName);
    String itemNameString = itemName.name().toLowerCase();
    if (maybeIndexTs.isPresent()) {
      newLastIndexTs = maxTimestamp(newLastIndexTs, Timestamp.valueOf(maybeIndexTs.get()));
      log.debug("Scanning for all the {}s after {}", itemNameString, newLastIndexTs);
      try (ManualRequestContext mctx = ctx.open();
          ReviewDb db = mctx.getReviewDbProvider().get()) {
        int count = 0;
        int errors = 0;
        Stopwatch stopwatch = Stopwatch.createStarted();
        for (T c : fetchItems(db)) {
          try {
            Optional<Timestamp> itemTs = indexIfNeeded(db, c, newLastIndexTs);
            if (itemTs.isPresent()) {
              count++;
              newLastIndexTs = maxTimestamp(newLastIndexTs, itemTs.get());
            }
          } catch (Exception e) {
            log.error("Unable to reindex {} {}", itemNameString, c, e);
            errors++;
          }
        }
        long elapsedNanos = stopwatch.stop().elapsed(TimeUnit.NANOSECONDS);
        if (count > 0) {
          log.info(
              "{} {}s reindexed in {} msec ({}/sec), {} failed",
              count,
              itemNameString,
              elapsedNanos / 1000000L,
              (count * 1000L) / (elapsedNanos / 1000000L),
              errors);
        } else if (errors > 0) {
          log.info("{} {}s failed to reindex", errors, itemNameString);
        } else {
          log.debug("Scanning finished");
        }
        indexTs.update(itemName, newLastIndexTs.toLocalDateTime());
      } catch (Exception e) {
        log.error("Unable to scan " + itemNameString + "s", e);
      }
    }
  }

  private Timestamp maxTimestamp(Timestamp ts1, Timestamp ts2) {
    if (ts1 == null) {
      return ts2;
    }

    if (ts2 == null) {
      return ts1;
    }

    if (ts1.after(ts2)) {
      return ts1;
    }
    return ts2;
  }

  protected abstract ResultSet<T> fetchItems(ReviewDb db) throws OrmException;

  protected abstract Optional<Timestamp> indexIfNeeded(ReviewDb db, T item, Timestamp sinceTs);
}
