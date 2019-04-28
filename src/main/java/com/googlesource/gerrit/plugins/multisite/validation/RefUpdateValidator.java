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

import static java.util.Comparator.comparing;

import com.google.common.base.MoreObjects;
import com.google.common.flogger.FluentLogger;
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

public class RefUpdateValidator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SharedRefDatabase sharedRefDb;
  private final ValidationMetrics validationMetrics;

  private final String projectName;
  private final RefDatabase refDb;
  private final SharedRefEnforcement refEnforcement;

  public static interface Factory {
    RefUpdateValidator create(String projectName, RefDatabase refDb);
  }

  @Inject
  public RefUpdateValidator(
      SharedRefDatabase sharedRefDb,
      ValidationMetrics validationMetrics,
      SharedRefEnforcement refEnforcement,
      @Assisted String projectName,
      @Assisted RefDatabase refDb) {
    this.sharedRefDb = sharedRefDb;
    this.validationMetrics = validationMetrics;
    this.refDb = refDb;
    this.projectName = projectName;
    this.refEnforcement = refEnforcement;
  }

  public static class RefPair {
    public final Ref oldRef;
    public final Ref newRef;
    public final Exception exception;

    RefPair(Ref oldRef, Ref newRef) {
      this.oldRef = nullRefCleansing(oldRef);
      this.newRef = nullRefCleansing(newRef);
      this.exception = null;
    }

    private Ref nullRefCleansing(Ref ref) {
      return ref == null ? SharedRefDatabase.NULL_REF : ref;
    }

    RefPair(Ref newRef, Exception e) {
      this.newRef = newRef;
      this.oldRef = SharedRefDatabase.NULL_REF;
      this.exception = e;
    }

    public String getName() {
      return MoreObjects.firstNonNull(
          oldRef == null ? null : oldRef.getName(), newRef == null ? null : newRef.getName());
    }

    public boolean hasFailed() {
      return exception != null;
    }
  }

  protected void handleExceptionWithPolicy(
      String errorMessage,
      ValidationWrapper delegateValidation,
      BatchRefUpdate batchRefUpdate,
      GitUpdateFunction gitUpdateFun)
      throws IOException {
    // If ignored we just do the GIT update
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

  protected void failWith(IOException e, EnforcePolicy policy) throws IOException {
    validationMetrics.incrementSplitBrainRefUpdates();

    FluentLogger.Api log = logger.atWarning();
    if (e.getCause() != null) {
      log = log.withCause(e);
    }
    log.log(e.getMessage());
    if (policy == EnforcePolicy.REQUIRED) {
      throw e;
    }
  }

  public void executeBatchUpdate(BatchRefUpdate batchRefUpdate, GitUpdateFunction gitUpdateFun)
      throws IOException {
    handleExceptionWithPolicy(
        "Failed to execute Batch Update", this::doExecuteBatchUpdate, batchRefUpdate, gitUpdateFun);
  }

  private void doExecuteBatchUpdate(BatchRefUpdate batchRefUpdate, GitUpdateFunction gitUpdateFun)
      throws IOException {

    List<ReceiveCommand> commands = batchRefUpdate.getCommands();

    List<RefPair> refsToUpdate =
        getRefsPairs(commands)
            .sorted(comparing(RefPair::hasFailed).reversed())
            .collect(Collectors.toList());

    if (refsToUpdate.isEmpty()) {
      return;
    }

    if (refsToUpdate.get(0).hasFailed()) {
      RefPair failedRef = refsToUpdate.get(0);

      logger.atWarning().withCause(failedRef.exception).log("Failed to fetch ref entries");
      throw new IOException(
          "Failed to fetch ref entries" + failedRef.newRef.getName(), failedRef.exception);
    }

    try (CloseableSet<AutoCloseable> locks = new CloseableSet()) {
      assertBatchCommandsAreInSync(refsToUpdate, locks);

      Map<ObjectId, Ref> oldRefsMap =
          refsToUpdate.stream()
              .collect(
                  Collectors.toMap(
                      refPair -> refPair.newRef.getObjectId(), refPair -> refPair.oldRef));

      gitUpdateFun.apply();

      updateSharedDBForSuccessfulCommands(batchRefUpdate.getCommands().stream(), oldRefsMap);
    }
  }

  private void updateSharedDBForSuccessfulCommands(
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
      // We are not checking refs that should be ignored
      final EnforcePolicy refEnforcementPolicy =
          refEnforcement.getPolicy(projectName, successfulRefPair.getName());
      if (refEnforcementPolicy == EnforcePolicy.IGNORED) continue;

      boolean succeeded =
          sharedRefDb.compareAndPut(
              projectName, successfulRefPair.oldRef, successfulRefPair.newRef);

      // We are not checking refs that should be ignored
      if (!succeeded) {
        String errorMessage =
            String.format(
                "Not able to persist the data in Zookeeper for project '%s' and ref '%s',"
                    + "the cluster is now in Split Brain since the commit has been "
                    + "persisted locally but not in SharedRef the value %s",
                projectName, successfulRefPair.getName(), successfulRefPair.newRef.getObjectId());

        failWith(new IOException(errorMessage), refEnforcementPolicy);
      }
    }
  }

  private void assertBatchCommandsAreInSync(
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
        isInSync = sharedRefDb.isMostRecentRefVersion(projectName, refPair.oldRef);
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

  public interface GitUpdateFunction {
    void apply() throws IOException;
  }

  public interface ValidationWrapper {
    void apply(BatchRefUpdate batchRefUpdate, GitUpdateFunction arg) throws IOException;
  }
}
