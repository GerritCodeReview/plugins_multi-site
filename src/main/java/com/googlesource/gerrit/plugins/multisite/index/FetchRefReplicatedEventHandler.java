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
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventListener;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.forwarder.Context;
import com.googlesource.gerrit.plugins.replication.pull.FetchRefReplicatedEvent;
import com.googlesource.gerrit.plugins.replication.pull.ReplicationState;
import org.eclipse.jgit.errors.MissingObjectException;

public class FetchRefReplicatedEventHandler implements EventListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private ChangeIndexer changeIndexer;
  private final String instanceId;

  @Inject
  FetchRefReplicatedEventHandler(ChangeIndexer changeIndexer, @GerritInstanceId String instanceId) {
    this.changeIndexer = changeIndexer;
    this.instanceId = instanceId;
  }

  @Override
  public void onEvent(Event event) {
    boolean isForwarded = Context.isForwardedEvent();
    try {
      Context.setForwardedEvent(true);

      if (event instanceof FetchRefReplicatedEvent && isLocalEvent(event)) {
        FetchRefReplicatedEvent fetchRefReplicatedEvent = (FetchRefReplicatedEvent) event;
        boolean isMetaRef = RefNames.isNoteDbMetaRef(fetchRefReplicatedEvent.getRefName());
        if (!RefNames.isRefsChanges(fetchRefReplicatedEvent.getRefName())
            || !fetchRefReplicatedEvent
                .getStatus()
                .equals(ReplicationState.RefFetchResult.SUCCEEDED.toString())) {
          return;
        }

        Project.NameKey projectNameKey = fetchRefReplicatedEvent.getProjectNameKey();
        logger.atFine().log(
            "Indexing ref '%s' for project %s",
            fetchRefReplicatedEvent.getRefName(), projectNameKey.get());
        Change.Id changeId = Change.Id.fromRef(fetchRefReplicatedEvent.getRefName());
        if (changeId != null && isMetaRef) {
          catchStorageExceptionForMissingUnknown(
              () -> changeIndexer.index(projectNameKey, changeId),
              "Skipping indexing of "
                  + projectNameKey
                  + "~"
                  + changeId.get()
                  + " because its patch-sets aren't replicated yet");
        } else if (changeId != null) {
          changeIndexer.index(projectNameKey, changeId);
        } else {
          logger.atWarning().log(
              "Couldn't get changeId from refName. Skipping indexing of change %s for project %s",
              fetchRefReplicatedEvent.getRefName(), projectNameKey.get());
        }
      }
    } finally {
      Context.setForwardedEvent(isForwarded);
    }
  }

  private void catchStorageExceptionForMissingUnknown(Runnable lambda, String infoMessage)
      throws RuntimeException {
    try {
      lambda.run();
    } catch (StorageException se) {
      Throwable cause = se.getCause();
      while (cause != null) {
        if (cause instanceof MissingObjectException) {
          logger.atInfo().log("%s", infoMessage);
          return;
        }
        cause = cause.getCause();
      }
      throw se;
    }
  }

  private boolean isLocalEvent(Event event) {
    return instanceId.equals(event.instanceId);
  }
}
