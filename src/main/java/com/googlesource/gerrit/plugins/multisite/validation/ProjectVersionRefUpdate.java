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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

public class ProjectVersionRefUpdate implements GitReferenceUpdatedListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final String MULTI_SITE_VERSIONING_REF = "refs/multi-site/project-version";

  GitRepositoryManager gitRepositoryManager;

  @Inject
  public ProjectVersionRefUpdate(GitRepositoryManager gitRepositoryManager) {
    this.gitRepositoryManager = gitRepositoryManager;
  }

  @Override
  public void onGitReferenceUpdated(Event event) {
    String projectName = event.getProjectName();
    logger.atInfo().log(
        "Intercepted ref update " + event.getRefName() + " for project " + projectName);

    try {
      Repository repository =
          gitRepositoryManager.openRepository(Project.NameKey.parse(projectName));
      BatchRefUpdate bru = getProjectVersionBatchRefUpdate(repository);
      executeBathRefUpdate(repository, bru);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Cannot create versioning command for " + projectName);
    }
  }

  private BatchRefUpdate getProjectVersionBatchRefUpdate(Repository repository) throws IOException {
    BatchRefUpdate bru = repository.getRefDatabase().newBatchUpdate();
    bru.addCommand(
        new ReceiveCommand(getOldId(repository), getNewId(repository), MULTI_SITE_VERSIONING_REF));
    bru.setAllowNonFastForwards(true);
    return bru;
  }

  private ObjectId getNewId(Repository repository) throws IOException {
    ObjectInserter ins = repository.newObjectInserter();
    ObjectId newId =
        ins.insert(OBJ_BLOB, Long.toString(Instant.now().toEpochMilli()).getBytes(UTF_8));
    ins.flush();
    return newId;
  }

  private ObjectId getOldId(Repository repository) throws IOException {
    Ref ref = repository.findRef(MULTI_SITE_VERSIONING_REF);
    return ref != null ? ref.getObjectId() : ObjectId.zeroId();
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
}
