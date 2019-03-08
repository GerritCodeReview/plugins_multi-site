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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.events.RefReceivedEvent;
import com.google.gerrit.server.git.validators.RefOperationValidationListener;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DfsRefDatabase;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Validates if a change can be applied without bringing the system into a split brain situation by
 * verifying that the local status is aligned with the central status as retrieved by the
 * DfsRefDatabase. It also updates the DB to set the new current status for a ref as a consequence
 * of ref updates, creation and deletions. The operation is done for mutable updates only. Operation
 * on immutable ones are always considered valid.
 */
public class InSyncChangeValidator implements RefOperationValidationListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DfsRefDatabase dfsRefDatabase;

  @Inject
  public InSyncChangeValidator(DfsRefDatabase dfsRefDatabase) {
    this.dfsRefDatabase = dfsRefDatabase;
  }

  @Override
  public List<ValidationMessage> onRefOperation(RefReceivedEvent refEvent)
      throws ValidationException {
    logger.atFine().log("Validating operation %s", refEvent);

    if (isImmutableRef(refEvent.getRefName())) {
      return Collections.emptyList();
    }

    switch (refEvent.command.getType()) {
      case CREATE:
        return onCreateRef(refEvent);

      case UPDATE:
      case UPDATE_NONFASTFORWARD:
        return onUpdateRef(refEvent);

      case DELETE:
        return onDeleteRef(refEvent);

      default:
        throw new IllegalArgumentException(
            String.format(
                "Unsupported command type '%s', in event %s",
                refEvent.command.getType().name(), refEvent));
    }
  }

  private boolean isImmutableRef(String refName) {
    return refName.startsWith("refs/changes");
  }

  private List<ValidationMessage> onDeleteRef(RefReceivedEvent refEvent)
      throws ValidationException {
    try {
      if (!dfsRefDatabase.deleteRef(refEvent.getRefName(), refEvent.command.getOldId())) {
        throw new ValidationException(
            String.format(
                "Unable to delete ref '%s', the old ID '%s' is not equal to the most recent one "
                    + "in the shared ref database",
                refEvent.getRefName(), refEvent.command.getOldId()));
      }
    } catch (NoSuchElementException refNotInDB) {
      logger.atSevere().withCause(refNotInDB).log(
          "Local status inconsistent with shared ref database for ref %s. "
              + "Trying to delete it but it is not in the DB",
          refEvent.getRefName());

      throw new ValidationException(
          String.format(
              "Unable to delete ref '%s', cannot find it in the shared ref database",
              refEvent.getRefName()),
          refNotInDB);
    }
    return Collections.emptyList();
  }

  private List<ValidationMessage> onUpdateRef(RefReceivedEvent refEvent)
      throws ValidationException {
    try {
      if (!dfsRefDatabase.updateRefId(
          refEvent.getRefName(), refEvent.command.getNewId(), refEvent.command.getOldId())) {
        throw new ValidationException(
            String.format(
                "Unable to update ref '%s', the old ID '%s' is not equal to the most recent one "
                    + "in the shared ref database",
                refEvent.getRefName(), refEvent.command.getOldId()));
      }
    } catch (NoSuchElementException refNotInDB) {
      logger.atSevere().withCause(refNotInDB).log(
          "Local status inconsistent with shared ref database for ref %s. "
              + "Trying to delete it but it is not in the DB",
          refEvent.getRefName());

      throw new ValidationException(
          String.format(
              "Unable to update ref '%s', cannot find it in the shared ref database",
              refEvent.getRefName()),
          refNotInDB);
    }

    return Collections.emptyList();
  }

  private List<ValidationMessage> onCreateRef(RefReceivedEvent refEvent)
      throws ValidationException {
    try {
      dfsRefDatabase.createRef(refEvent.getRefName(), refEvent.command.getNewId());
    } catch (IllegalArgumentException alreadyInDB) {
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
