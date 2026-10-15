// Copyright (C) 2025 GerritForge, Inc.
//
// Licensed under the BSL 1.1 (the "License");
// you may not use this file except in compliance with the License.
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.gerritforge.gerrit.plugins.multisite.forwarder;

import static com.gerritforge.gerrit.plugins.multisite.forwarder.ForwardedIndexingHandler.Operation.DELETE;
import static com.gerritforge.gerrit.plugins.multisite.forwarder.ForwardedIndexingHandler.Operation.INDEX;

import com.gerritforge.gerrit.plugins.multisite.Configuration;
import com.gerritforge.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import com.gerritforge.gerrit.plugins.multisite.forwarder.events.IndexEvent;
import com.gerritforge.gerrit.plugins.multisite.index.ChangeChecker;
import com.gerritforge.gerrit.plugins.multisite.index.ForwardedIndexExecutor;
import com.google.common.base.Preconditions;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
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
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeChecker changeChecker;

  @Inject
  ForwardedIndexChangeHandler(
      ChangeIndexer indexer,
      Configuration configuration,
      @ForwardedIndexExecutor ScheduledExecutorService indexExecutor,
      OneOffRequestContext oneOffCtx,
      ChangeChecker changeChecker,
      Provider<InternalChangeQuery> queryProvider) {
    super(indexExecutor, configuration, oneOffCtx);
    this.indexer = indexer;
    this.changeChecker = changeChecker;
    this.queryProvider = queryProvider;
  }

  @Override
  public void handle(IndexEvent sourceEvent) throws IOException {
    if (sourceEvent instanceof ChangeIndexEvent changeIndexEvent) {
      ForwardedIndexingHandler.Operation operation = changeIndexEvent.deleted ? DELETE : INDEX;
      index(
          changeIndexEvent.projectName + "~" + changeIndexEvent.changeId,
          operation,
          Optional.of(changeIndexEvent));
    }
  }

  @Override
  public IndexingResult handleSync(IndexEvent sourceEvent) throws IOException {
    if (sourceEvent instanceof ChangeIndexEvent event) {
      if (ChangeIndexEvent.isAllChangesDeletedForProject(event)) {
        deleteAllChangesForProject(event.projectName);
        return IndexingResult.SUCCESS;
      }

      String changeId = event.projectName + "~" + event.changeId;
      if (event.deleted) {
        index(changeId, DELETE, Optional.of(event));
        return IndexingResult.SUCCESS;
      }

      return indexSyncIfConsistentAndUpToDate(changeId, event, changeChecker);
    }
    return IndexingResult.IGNORED;
  }

  @Override
  protected void doIndex(String id, Optional<ChangeIndexEvent> indexEvent) {
    if (indexEvent.isPresent()
        && ChangeIndexEvent.isAllChangesDeletedForProject(indexEvent.get())) {
      deleteAllChangesForProject(indexEvent.get().projectName);
    } else {
      scheduleIndexing(id, indexEvent, this::indexIfConsistent);
    }
  }

  private void deleteAllChangesForProject(String projectName) {
    try (ForwardedContext ctx = ForwardedContext.open()) {
      indexer.deleteAllForProject(Project.nameKey(projectName));
    }
  }

  private void indexIfConsistent(String id) {
    if (isChangeConsistent(id)) {
      reindex(id);
    }
  }

  private boolean isChangeConsistent(String id) {
    return changeChecker.isConsistent(id);
  }

  @Override
  protected void attemptToIndex(String id) {
    IndexingRetry retry = indexingRetryTaskMap.get(id);
    if (retry == null) {
      log.atInfo().log("Skipping indexing of change %s: no pending retry", id);
      return;
    }
    boolean changeIsPresent = changeChecker.getChangeNotes(id).isPresent();
    boolean changeIsConsistent = changeChecker.isConsistent(id);
    if (changeIsConsistent) {
      reindexIfUpToDate(id, changeChecker);
    } else {
      log.atWarning().log(
          "Change %s %s in local Git repository (event=%s) after %d attempt(s)",
          id,
          !changeIsPresent ? "not present yet" : "is not consistent",
          retry.getEvent(),
          retry.getRetryNumber());

      retry.incrementRetryNumber();
      if (!rescheduleIndex(id)) {
        log.atSevere().log(
            "Change %s %s in the local Git repository (event=%s)",
            id, !changeIsPresent ? "could not be found" : "was not consistent", retry.getEvent());
      }
    }
  }

  @Override
  protected void reindex(String id) {
    try (ManualRequestContext ctx = oneOffCtx.open()) {
      Optional<ChangeNotes> changeNotes = changeChecker.getChangeNotes(id);
      ChangeNotes notes = changeNotes.get();
      var unused = notes.reload();
      indexer.index(notes);
    }
  }

  @Override
  protected String indexName() {
    return "change";
  }

  @Override
  protected void doDelete(String id, Optional<ChangeIndexEvent> indexEvent) {
    Preconditions.checkArgument(
        indexEvent.isPresent(), "No event found when deleting change %s", id);
    ChangeIndexEvent event = indexEvent.get();
    if (indexingRetryTaskMap.remove(id) != null) {
      log.atInfo().log("Removed pending indexing retry for change %s: received delete event", id);
    }
    if (event.projectName.isEmpty()) {
      List<ChangeData> changes = queryProvider.get().byChangeNumber(Change.id(event.changeId));
      ChangeData change = uniqueChange(id, changes);
      if (change == null) {
        log.atWarning().log("Skipping deletion from index for change %s", id);
        return;
      }
      indexer.delete(change.change().getProject(), change.getId());
    } else {
      List<ChangeData> changes =
          queryProvider
              .get()
              .byProjectChangeNumber(Project.nameKey(event.projectName), Change.id(event.changeId));
      ChangeData change = uniqueChange(id, changes);
      if (change == null) {
        log.atWarning().log("Skipping deletion from index for change %s", id);
        return;
      }
      indexer.delete(Project.nameKey(event.projectName), change.getId());
    }
    log.atFiner().log("Change %s successfully deleted from index", id);
  }

  private ChangeData uniqueChange(String id, List<ChangeData> changes) {
    if (changes.isEmpty()) {
      log.atWarning().log("Change %s not found in index", id);
      return null;
    }
    if (changes.size() > 1) {
      log.atWarning().log("Multiple changes with the same number %s were found", id);
      return null;
    }
    return changes.getFirst();
  }
}
