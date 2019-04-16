// Copyright (C) 2019 The Android Open Source Project
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
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement.EnforcePolicy;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.transport.ReceiveCommand;

public class BatchRefUpdateValidator extends RefUpdateValidator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static interface Factory {
    BatchRefUpdateValidator create(String projectName, RefDatabase refDb);
  }

  public interface BatchValidationWrapper {
    void apply(BatchRefUpdate batchRefUpdate, NoParameterVoidFunction arg) throws IOException;
  }

  @Inject
  public BatchRefUpdateValidator(
      SharedRefDatabase sharedRefDb,
      ValidationMetrics validationMetrics,
      SharedRefEnforcement refEnforcement,
      @Assisted String projectName,
      @Assisted RefDatabase refDb) {
    super(sharedRefDb, validationMetrics, refEnforcement, projectName, refDb);
  }

  public void executeBatchUpdateWithValidation(
      BatchRefUpdate batchRefUpdate, NoParameterVoidFunction batchRefUpdateFunction)
      throws IOException {
    if (refEnforcement.getPolicy(projectName) == EnforcePolicy.IGNORED) {
      batchRefUpdateFunction.invoke();
      return;
    }

    try {
      doExecuteBatchUpdate(batchRefUpdate, batchRefUpdateFunction);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to execute Batch Update on project %s", projectName);
      if (refEnforcement.getPolicy(projectName) == EnforcePolicy.REQUIRED) {
        throw e;
      }
    }
  }

  private void doExecuteBatchUpdate(
      BatchRefUpdate batchRefUpdate, NoParameterVoidFunction delegateUpdate) throws IOException {

    List<ReceiveCommand> commands = batchRefUpdate.getCommands();
    if (commands.isEmpty()) {
      return;
    }

    List<RefPair> refsToUpdate = getRefsPairs(commands).collect(Collectors.toList());
    List<RefPair> refsFailures =
        refsToUpdate.stream().filter(RefPair::hasFailed).collect(Collectors.toList());
    if (!refsFailures.isEmpty()) {
      String allFailuresMessage =
          refsFailures.stream()
              .map(refPair -> String.format("Failed to fetch ref %s", refPair.oldRef))
              .collect(Collectors.joining(", "));
      Exception firstFailureException = refsFailures.get(0).exception;

      logger.atSevere().withCause(firstFailureException).log(allFailuresMessage);
      throw new IOException(allFailuresMessage, firstFailureException);
    }

    try (CloseableSet<AutoCloseable> locks = new CloseableSet<>()) {
      checkIfLocalRefIsUpToDateWithSharedRefDb(refsToUpdate, locks);
      delegateUpdate.invoke();
      updateSharedRefDb(batchRefUpdate.getCommands().stream(), refsToUpdate);
    }
  }

  private void updateSharedRefDb(Stream<ReceiveCommand> commandStream, List<RefPair> refsToUpdate)
      throws IOException {
    if (commandStream
        .filter(cmd -> cmd.getResult() != ReceiveCommand.Result.OK)
        .findFirst()
        .isPresent()) {
      return;
    }

    for (RefPair refPair : refsToUpdate) {
      updateSharedDbOrThrowExceptionFor(refPair);
    }
  }

  private Stream<RefPair> getRefsPairs(List<ReceiveCommand> receivedCommands) {
    return receivedCommands.stream().map(this::getRefPairForCommand);
  }

  private RefPair getRefPairForCommand(ReceiveCommand command) {
    try {
      switch (command.getType()) {
        case CREATE:
          return new RefPair(SharedRefDatabase.NULL_REF, getNewRef(command));

        case UPDATE:
        case UPDATE_NONFASTFORWARD:
          return new RefPair(refDb.getRef(command.getRefName()), getNewRef(command));

        case DELETE:
          return new RefPair(refDb.getRef(command.getRefName()), SharedRefDatabase.NULL_REF);

        default:
          return new RefPair(
              getNewRef(command),
              new IllegalArgumentException("Unsupported command type " + command.getType()));
      }
    } catch (IOException e) {
      return new RefPair(command.getRef(), e);
    }
  }

  private Ref getNewRef(ReceiveCommand command) {
    return sharedRefDb.newRef(command.getRefName(), command.getNewId());
  }

  private void checkIfLocalRefIsUpToDateWithSharedRefDb(
      List<RefPair> refsToUpdate, CloseableSet<AutoCloseable> locks) throws IOException {
    for (RefPair refPair : refsToUpdate) {
      checkIfLocalRefIsUpToDateWithSharedRefDb(refPair.getName(), locks);
    }
  }
}
