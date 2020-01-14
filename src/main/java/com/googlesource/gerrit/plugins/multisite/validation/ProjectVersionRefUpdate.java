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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventListener;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import com.googlesource.gerrit.plugins.multisite.forwarder.Context;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

public class ProjectVersionRefUpdate implements EventListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final String MULTI_SITE_VERSIONING_REF = "refs/multi-site/project-version";

  GitRepositoryManager gitRepositoryManager;

  @Inject
  public ProjectVersionRefUpdate(GitRepositoryManager gitRepositoryManager) {
    this.gitRepositoryManager = gitRepositoryManager;
  }

  private RefUpdate getProjectVersionRefUpdate(Repository repository, Long version) throws IOException {
    RefUpdate refUpdate = repository.getRefDatabase().newUpdate(MULTI_SITE_VERSIONING_REF,false);
    refUpdate.setNewObjectId(getNewId(repository, version));
    refUpdate.setForceUpdate(true);
    return refUpdate;
  }

  private ObjectId getNewId(Repository repository, Long version) throws IOException {
    ObjectInserter ins = repository.newObjectInserter();
    ObjectId newId =
        ins.insert(OBJ_BLOB, Long.toString(version).getBytes(UTF_8));
    ins.flush();
    return newId;
  }

  @Override
  public void onEvent(Event event) {
    logger.atFine().log("Processing event type: " + event.type);
    if (!Context.isForwardedEvent() && event instanceof RefUpdatedEvent) {
      RefUpdatedEvent refUpdatedEvent = (RefUpdatedEvent) event;
      Project.NameKey projectNameKey = refUpdatedEvent.getProjectNameKey();
      logger.atFine().log("Updating local version for project " + projectNameKey.get());
      try(Repository repository = gitRepositoryManager.openRepository(projectNameKey)) {
        RefUpdate refUpdate = getProjectVersionRefUpdate(repository, refUpdatedEvent.eventCreatedOn);
        RefUpdate.Result result = refUpdate.update();
        if (!isSuccessful(result)) {
          logger.atSevere().log( String.format("Something went wrong with version update of %s, result: %s",projectNameKey.get(), result.name()));
        }
      } catch (IOException e) {
        logger.atSevere().withCause(e).log("Cannot create versioning command for " + projectNameKey.get());
      }
    }

  }

  private Boolean isSuccessful(RefUpdate.Result result) {
    List <RefUpdate.Result> successfulResults = ImmutableList.copyOf(new ArrayList<RefUpdate.Result>(Arrays.asList(RefUpdate.Result.NEW, RefUpdate.Result.FORCED, RefUpdate.Result.NO_CHANGE)));
    return successfulResults.contains(result);
  }
}
