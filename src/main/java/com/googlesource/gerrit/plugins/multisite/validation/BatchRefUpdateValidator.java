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
import com.google.gerrit.common.Nullable;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement.EnforcePolicy;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;

public class BatchRefUpdateValidator extends RefUpdateValidator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static interface Factory {
    BatchRefUpdateValidator create(String projectName, RefDatabase refDb);
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

  protected void executeBatchUpdateWithPolicy(
      String errorMessage,
      BatchValidationWrapper delegateValidation,
      BatchRefUpdate batchRefUpdate,
      NoParameterVoidFunction gitUpdateFun)
      throws IOException {
    if (refEnforcement.getPolicy(projectName) == EnforcePolicy.IGNORED) {
      gitUpdateFun.apply();
      return;
    }

    try {
      delegateValidation.apply(batchRefUpdate, gitUpdateFun);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(errorMessage);
      if (refEnforcement.getPolicy(projectName) == EnforcePolicy.REQUIRED) {
        throw e;
      }
    }
  }

  private void failWith(IOException e, EnforcePolicy policy) throws IOException {
    FluentLogger.Api log = logger.atWarning();

    validationMetrics.incrementSplitBrainPrevention();

    if (e.getCause() != null) {
      log = log.withCause(e);
    }
    log.log(
        String.format(
            "Failure while running with policy enforcement %s. Error message: %s",
            policy, e.getMessage()));
    if (policy == EnforcePolicy.REQUIRED) {
      throw e;
    }
  }

  public void executeBatchUpdateWithValidation(
      BatchRefUpdate batchRefUpdate, NoParameterVoidFunction gitUpdateFun) throws IOException {
    executeBatchUpdateWithPolicy(
        "Failed to execute Batch Update", this::doExecuteBatchUpdate, batchRefUpdate, gitUpdateFun);
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

    Map<ObjectId, Ref> oldRefsMap =
        refsToUpdate.stream()
            .collect(
                Collectors.toMap(
                    refPair -> refPair.newRef.getObjectId(), refPair -> refPair.oldRef));

    try (CloseableSet<AutoCloseable> locks = new CloseableSet()) {
      assertRefPairsAreInSyncWithSharedDb(refsToUpdate, locks);
      delegateUpdate.apply();
      updateSharedRefDbForSuccessfulCommands(batchRefUpdate.getCommands().stream(), oldRefsMap);
    }
  }

  private void updateSharedRefDbForSuccessfulCommands(
      Stream<ReceiveCommand> commandStream, Map<ObjectId, Ref> oldRefs) throws IOException {
    List<RefPair> successfulRefPairs =
        commandStream
            .filter(cmd -> cmd.getResult() == Result.OK)
            .map(
                cmd ->
                    new RefPair(
                        oldRefs.getOrDefault(cmd.getNewId(), sharedRefDb.NULL_REF),
                        sharedRefDb.newRef(cmd.getRefName(), cmd.getNewId())))
            .collect(Collectors.toList());

    for (RefPair successfulRefPair : successfulRefPairs) {
      updateSharedDbOrThrowExceptionFor(successfulRefPair);
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

  private void assertRefPairsAreInSyncWithSharedDb(
      List<RefPair> refsToUpdate, CloseableSet<AutoCloseable> locks) throws IOException {
    for (RefPair refPair : refsToUpdate) {
      Ref nonNullRef =
          refPair.oldRef == sharedRefDb.NULL_REF || refPair.oldRef == null
              ? refPair.newRef
              : refPair.oldRef;

      final EnforcePolicy refEnforcementPolicy =
          refEnforcement.getPolicy(projectName, nonNullRef.getName());
      if (refEnforcementPolicy == EnforcePolicy.IGNORED) continue;

      String resourceLockKey = String.format("%s-%s", projectName, nonNullRef.getName());
      try {
        locks.addResourceIfNotExist(
            resourceLockKey, () -> sharedRefDb.lockRef(projectName, nonNullRef));
      } catch (Exception e) {
        throw new IOException(
            String.format(
                "Unable to prepare locks for project %s and reference %s",
                projectName, nonNullRef.getName()),
            e);
      }

      boolean isInSync;
      if (refPair.oldRef != sharedRefDb.NULL_REF && refPair.oldRef != null) {
        isInSync = sharedRefDb.isUpToDate(projectName, refPair.oldRef);
      } else {
        isInSync = !sharedRefDb.exists(projectName, refPair.getName());
      }

      if (!isInSync) {
        failWith(
            new IOException(
                String.format(
                    "Ref '%s' for project '%s' not in sync with shared Ref-Db."
                        + "Trying to change the Ref-Db from oldRefId '%s'"
                        + " to newRefId '%s'. Aborting batch update.",
                    refPair.getName(),
                    projectName,
                    refPair.oldRef.getObjectId(),
                    refPair.newRef.getObjectId())),
            refEnforcementPolicy);
      }
    }
  }

  public static class CloseableSet<T extends AutoCloseable> implements AutoCloseable {
    private final HashMap<String, AutoCloseable> elements;

    public CloseableSet() {
      this(new HashMap<>());
    }

    public CloseableSet(HashMap<String, AutoCloseable> elements) {
      this.elements = elements;
    }

    public void addResourceIfNotExist(
        String key, ExceptionThrowingSupplier<T, Exception> resourceFactory) throws Exception {
      if (!elements.containsKey(key)) {
        elements.put(key, resourceFactory.create());
      }
    }

    @Override
    public void close() {
      elements.values().stream()
          .forEach(
              closeable -> {
                try {
                  closeable.close();
                } catch (Exception closingException) {
                  logger.atSevere().withCause(closingException).log(
                      "Exception trying to release resource %s, "
                          + "the locked resources won't be accessible in all cluster unless"
                          + " the lock is removed from ZK manually",
                      closeable);
                }
              });
    }
  }

  public interface ExceptionThrowingSupplier<T, E extends Exception> {
    T create() throws E;
  }

  public interface BatchValidationWrapper {
    void apply(BatchRefUpdate batchRefUpdate, NoParameterVoidFunction arg) throws IOException;
  }

  interface NoParameterVoidFunction<T> {
    void apply() throws IOException;
  }
}
