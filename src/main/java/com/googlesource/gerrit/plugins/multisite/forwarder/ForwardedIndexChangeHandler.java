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

import com.google.common.base.Splitter;
import com.google.gerrit.entities.Change;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import com.googlesource.gerrit.plugins.multisite.index.ChangeChecker;
import com.googlesource.gerrit.plugins.multisite.index.ChangeCheckerImpl;
import com.googlesource.gerrit.plugins.multisite.index.ForwardedIndexExecutor;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Index a change using {@link ChangeIndexer}. This class is meant to be used on the receiving side
 * of the {@link IndexEventForwarder} since it will prevent indexed change to be forwarded again
 * causing an infinite forwarding loop between the 2 nodes. It will also make sure no concurrent
 * indexing is done for the same change id
 */
@Singleton
public class ForwardedIndexChangeHandler
    extends ForwardedIndexingHandlerWithRetries<String, ChangeIndexEvent> {
  private final ChangeIndexer indexer;
  private final ChangeCheckerImpl.Factory changeCheckerFactory;

  @Inject
  ForwardedIndexChangeHandler(
      ChangeIndexer indexer,
      Configuration configuration,
      @ForwardedIndexExecutor ScheduledExecutorService indexExecutor,
      OneOffRequestContext oneOffCtx,
      ChangeCheckerImpl.Factory changeCheckerFactory) {
    super(indexExecutor, configuration, oneOffCtx);
    this.indexer = indexer;
    this.changeCheckerFactory = changeCheckerFactory;
  }

  @Override
  protected void doIndex(String id, Optional<ChangeIndexEvent> indexEvent) {
    attemptToIndex(id, indexEvent, 0);
  }

  @Override
  protected void attemptToIndex(String id, Optional<ChangeIndexEvent> indexEvent, int retryCount) {
    ChangeChecker checker = changeCheckerFactory.create(id);
    Optional<ChangeNotes> changeNotes = checker.getChangeNotes();
    boolean changeIsPresent = changeNotes.isPresent();
    boolean changeIsConsistent = checker.isChangeConsistent();
    if (changeIsPresent && changeIsConsistent) {
      reindexAndCheckIsUpToDate(id, indexEvent, checker, retryCount);
    } else {
      log.warn(
          "Change {} {} in local Git repository (event={}) after {} attempt(s)",
          id,
          !changeIsPresent
              ? "not present yet"
              : (changeIsConsistent ? "is" : "is not") + " consistent",
          indexEvent,
          retryCount);
      if (!rescheduleIndex(id, indexEvent, retryCount + 1)) {
        log.error(
            "Change {} {} in the local Git repository (event={})",
            id,
            !changeIsPresent
                ? "could not be found"
                : (changeIsConsistent ? "was" : "was not") + " consistent",
            indexEvent);
      }
    }
  }

  @Override
  protected void reindex(String id) {
    try (ManualRequestContext ctx = oneOffCtx.open()) {
      ChangeChecker checker = changeCheckerFactory.create(id);
      Optional<ChangeNotes> changeNotes = checker.getChangeNotes();
      ChangeNotes notes = changeNotes.get();
      notes.reload();
      indexer.index(notes);
    }
  }

  @Override
  protected String indexName() {
    return "change";
  }

  @Override
  protected void doDelete(String id, Optional<ChangeIndexEvent> indexEvent) {
    indexer.delete(parseChangeId(id));
    log.debug("Change {} successfully deleted from index", id);
  }

  private static Change.Id parseChangeId(String id) {
    Change.Id changeId = Change.id(Integer.parseInt(Splitter.on("~").splitToList(id).get(1)));
    return changeId;
  }
}
