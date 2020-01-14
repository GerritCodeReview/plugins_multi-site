// Copyright (C) 2020 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.validation;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Longs;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventListener;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import java.io.IOException;

import com.googlesource.gerrit.plugins.multisite.forwarder.Context;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

public class ProjectVersionRefUpdate implements EventListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final String MULTI_SITE_VERSIONING_REF = "refs/multi-site/project-version";

  GitRepositoryManager gitRepositoryManager;

  @Inject
  public ProjectVersionRefUpdate(GitRepositoryManager gitRepositoryManager) {
    this.gitRepositoryManager = gitRepositoryManager;
  }

  private BatchRefUpdate getProjectVersionBatchRefUpdate(Repository repository, Long version) throws IOException {
    BatchRefUpdate bru = repository.getRefDatabase().newBatchUpdate();
    bru.addCommand(
        new ReceiveCommand(ObjectId.zeroId(), getNewId(repository, version), MULTI_SITE_VERSIONING_REF));
    bru.setAllowNonFastForwards(true);
    return bru;
  }

  private ObjectId getNewId(Repository repository, Long version) throws IOException {
    ObjectInserter ins = repository.newObjectInserter();
    ObjectId newId =
        ins.insert(OBJ_BLOB, Longs.toByteArray(version));
    ins.flush();
    return newId;
  }

  private void executeBathRefUpdate(Repository git, BatchRefUpdate bru) throws IOException {
    try (RevWalk rw = new RevWalk(git)) {
      bru.execute(rw, NullProgressMonitor.INSTANCE);
    }
    for (ReceiveCommand cmd : bru.getCommands()) {
      if (cmd.getResult() != ReceiveCommand.Result.OK) {
        throw new IOException("Failed to update ref: " + cmd.getRefName());
      }
    }
  }

  @Override
  public void onEvent(Event event) {
    logger.atFine().log("Processing event type: " + event.type);
    if (!Context.isForwardedEvent() && event instanceof RefUpdatedEvent) {
      RefUpdatedEvent refUpdatedEvent = (RefUpdatedEvent) event;
      Project.NameKey projectNameKey = refUpdatedEvent.getProjectNameKey();
      try(Repository repository = gitRepositoryManager.openRepository(projectNameKey)) {
        BatchRefUpdate bru = getProjectVersionBatchRefUpdate(repository, refUpdatedEvent.eventCreatedOn);
        executeBathRefUpdate(repository, bru);
      } catch (IOException e) {
        logger.atSevere().withCause(e).log("Cannot create versioning command for " + projectNameKey.get());
      }
    }

  }
}
