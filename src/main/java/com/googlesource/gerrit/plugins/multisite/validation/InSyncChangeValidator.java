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

package com.googlesource.gerrit.plugins.multisite.validation;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.events.RefReceivedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.validators.RefOperationValidationListener;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * Validates if a change can be applied without bringing the system into a split brain situation by
 * verifying that the local status is aligned with the central status as retrieved by the
 * SharedRefDatabase. It also updates the DB to set the new current status for a ref as a
 * consequence of ref updates, creation and deletions. The operation is done for mutable updates
 * only. Operation on immutable ones are always considered valid.
 */
public class InSyncChangeValidator implements RefOperationValidationListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SharedRefDatabase dfsRefDatabase;
  private final GitRepositoryManager repoManager;

  @Inject
  public InSyncChangeValidator(SharedRefDatabase dfsRefDatabase, GitRepositoryManager repoManager) {
    this.dfsRefDatabase = dfsRefDatabase;
    this.repoManager = repoManager;
  }

  @Override
  public List<ValidationMessage> onRefOperation(RefReceivedEvent refEvent)
      throws ValidationException {
    logger.atFine().log("Validating operation %s", refEvent);

    if (isImmutableRef(refEvent.getRefName())) {
      return Collections.emptyList();
    }

    try (Repository repo = repoManager.openRepository(refEvent.getProjectNameKey())) {

      switch (refEvent.command.getType()) {
        case CREATE:
          return onCreateRef(refEvent);

        case UPDATE:
        case UPDATE_NONFASTFORWARD:
          return onUpdateRef(repo, refEvent);

        case DELETE:
          return onDeleteRef(repo, refEvent);

        default:
          throw new IllegalArgumentException(
              String.format(
                  "Unsupported command type '%s', in event %s",
                  refEvent.command.getType().name(), refEvent));
      }
    } catch (IOException e) {
      throw new ValidationException(
          "Unable to access repository " + refEvent.getProjectNameKey(), e);
    }
  }

  private boolean isImmutableRef(String refName) {
    return refName.startsWith("refs/changes");
  }

  private List<ValidationMessage> onDeleteRef(Repository repo, RefReceivedEvent refEvent)
      throws ValidationException {
    try {
      Ref localRef = repo.findRef(refEvent.getRefName());
      if (localRef == null) {
        logger.atWarning().log(
                "Local status inconsistent with shared ref database for ref %s. "
                        + "Trying to delete it but it is not in the local DB",
                refEvent.getRefName());

        throw new ValidationException(
                String.format(
                        "Unable to delete ref '%s', cannot find it in the local ref database",
                        refEvent.getRefName()));
      }

      if (!dfsRefDatabase.compareAndRemove(refEvent.getProjectNameKey().get(), localRef)) {
        throw new ValidationException(
            String.format(
                "Unable to delete ref '%s', the local ObjectId '%s' is not equal to the one "
                    + "in the shared ref database",
                refEvent.getRefName(), localRef.getObjectId().getName()));
      }
    } catch (IOException ioe) {
      logger.atSevere().withCause(ioe).log(
          "Local status inconsistent with shared ref database for ref %s. "
              + "Trying to delete it but it is not in the DB",
          refEvent.getRefName());

      throw new ValidationException(
          String.format(
              "Unable to delete ref '%s', cannot find it in the shared ref database",
              refEvent.getRefName()),
          ioe);
    }
    return Collections.emptyList();
  }

  private List<ValidationMessage> onUpdateRef(Repository repo, RefReceivedEvent refEvent)
      throws ValidationException {
    try {
      Ref localRef = repo.findRef(refEvent.getRefName());
      if (localRef == null) {
        logger.atWarning().log(
            "Local status inconsistent with shared ref database for ref %s. "
                + "Trying to update it but it is not in the local DB",
            refEvent.getRefName());

        throw new ValidationException(
            String.format(
                "Unable to update ref '%s', cannot find it in the local ref database",
                refEvent.getRefName()));
      }

      Ref newRef = dfsRefDatabase.newRef(refEvent.getRefName(), refEvent.command.getNewId());
      if (!dfsRefDatabase.compareAndPut(refEvent.getProjectNameKey().get(), localRef, newRef)) {
        throw new ValidationException(
            String.format(
                "Unable to update ref '%s', the local objectId '%s' is not equal to the one "
                    + "in the shared ref database",
                refEvent.getRefName(), localRef.getObjectId().getName()));
      }
    } catch (IOException ioe) {
      logger.atSevere().withCause(ioe).log(
          "Local status inconsistent with shared ref database for ref %s. "
              + "Trying to update it cannot extract the existing one on DB",
          refEvent.getRefName());

      throw new ValidationException(
          String.format(
              "Unable to update ref '%s', cannot open the local ref on the local DB",
              refEvent.getRefName()),
          ioe);
    }

    return Collections.emptyList();
  }

  private List<ValidationMessage> onCreateRef(RefReceivedEvent refEvent)
      throws ValidationException {
    try {
      Ref newRef = dfsRefDatabase.newRef(refEvent.getRefName(), refEvent.command.getNewId());
      dfsRefDatabase.compareAndPut(
          refEvent.getProjectNameKey().get(), SharedRefDatabase.NULL_REF, newRef);
    } catch (IllegalArgumentException | IOException alreadyInDB) {
      logger.atSevere().withCause(alreadyInDB).log(
          "Local status inconsistent with shared ref database for ref %s. "
              + "Trying to delete it but it is not in the DB",
          refEvent.getRefName());

      throw new ValidationException(
          String.format(
              "Unable to update ref '%s', cannot find it in the shared ref database",
              refEvent.getRefName()),
          alreadyInDB);
    }
    return Collections.emptyList();
  }
}
