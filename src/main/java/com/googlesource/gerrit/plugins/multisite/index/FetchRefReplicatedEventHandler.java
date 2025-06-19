// Copyright (C) 2023 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.index;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventListener;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.forwarder.Context;
import com.googlesource.gerrit.plugins.replication.pull.FetchRefReplicatedEvent;
import com.googlesource.gerrit.plugins.replication.pull.ReplicationState;
import java.io.IOException;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

public class FetchRefReplicatedEventHandler implements EventListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final GitRepositoryManager gitRepositoryManager;
  private ChangeIndexer changeIndexer;
  private final String instanceId;

  @Inject
  FetchRefReplicatedEventHandler(
      ChangeIndexer changeIndexer,
      @GerritInstanceId String instanceId,
      GitRepositoryManager gitRepositoryManager) {
    this.changeIndexer = changeIndexer;
    this.instanceId = instanceId;
    this.gitRepositoryManager = gitRepositoryManager;
  }

  @Override
  public void onEvent(Event event) {
    boolean isForwarded = Context.isForwardedEvent();
    try {
      Context.setForwardedEvent(true);

      if (event instanceof FetchRefReplicatedEvent && isLocalEvent(event)) {
        FetchRefReplicatedEvent fetchRefReplicatedEvent = (FetchRefReplicatedEvent) event;
        // This removal of the ':' prefix is needed because of Issue 426470297 in the
        // pull-replication plugin
        // that may leave the deleted refs notification as a ref-spec instead of the deleted
        // ref-name.
        String refName =
            fetchRefReplicatedEvent.ref.startsWith(":")
                ? fetchRefReplicatedEvent.ref.substring(1)
                : fetchRefReplicatedEvent.ref;
        if (!RefNames.isNoteDbMetaRef(refName)
            || !fetchRefReplicatedEvent
                .getStatus()
                .equals(ReplicationState.RefFetchResult.SUCCEEDED.toString())) {
          return;
        }

        Project.NameKey projectNameKey = fetchRefReplicatedEvent.getProjectNameKey();
        Change.Id changeId = Change.Id.fromRef(refName);

        try (Repository repo = gitRepositoryManager.openRepository(projectNameKey)) {
          if (changeId != null) {
            RefUpdate.Result fetchRefReplicatedEventResult =
                fetchRefReplicatedEvent.getRefUpdateResult();
            if ((RefUpdate.Result.FORCED.equals(fetchRefReplicatedEventResult)
                    || RefUpdate.Result.NO_CHANGE.equals(fetchRefReplicatedEventResult))
                && repo.exactRef(refName) == null) {
              logger.atInfo().log(
                  "Deleting change '%s' from index following the forced delete of '%s' on project"
                      + " %s",
                  changeId, refName, projectNameKey.get());
              // NOTE: The change has been already deleted and we just don't know if it was a
              // locally
              // created change or imported, because the meta-data is not there anymore.
              // In the future, it would be best to have that information passed to the event by the
              // pull-replication plugin *before* the delete, and added to the
              // FetchRefReplicatedEvent,
              // so that multi-site can use it for computing the virtual change-id and assuring the
              // delete
              // of the imported changes as well.
              changeIndexer.delete(changeId);
            } else {
              logger.atInfo().log(
                  "Indexing change '%s' following update of '%s' on project %s",
                  changeId, refName, projectNameKey.get());
              changeIndexer.index(projectNameKey, changeId);
            }
          } else {
            logger.atWarning().log(
                "Couldn't get changeId from refName. Skipping indexing of change %s for project %s",
                refName, projectNameKey.get());
          }
        }
      }
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Unable to process event %s", event);
    } finally {
      Context.setForwardedEvent(isForwarded);
    }
  }

  private boolean isLocalEvent(Event event) {
    return instanceId.equals(event.instanceId);
  }
}
