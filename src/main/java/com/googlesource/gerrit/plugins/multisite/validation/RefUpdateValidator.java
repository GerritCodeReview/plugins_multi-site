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

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.OutOfSyncException;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedLockException;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement.EnforcePolicy;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;

public class RefUpdateValidator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected final SharedRefDatabase sharedRefDb;
  protected final ValidationMetrics validationMetrics;

  protected final String projectName;
  protected final RefDatabase refDb;
  protected final SharedRefEnforcement refEnforcement;

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

  public RefUpdate.Result executeRefUpdate(
      RefUpdate refUpdate, NoParameterFunction<RefUpdate.Result> refUpdateFunction)
      throws IOException {
    if (refEnforcement.getPolicy(projectName) == EnforcePolicy.IGNORED) {
      return refUpdateFunction.invoke();
    }

    try {
      return doExecuteRefUpdate(refUpdate, refUpdateFunction);
    } catch (IOException e) {
      if (e.getClass() == SharedDbSplitBrainException.class) {
        validationMetrics.incrementSplitBrain();
      }

      logger.atWarning().withCause(e).log(
          "Unable to execute ref-update on project=%s ref=%s",
          projectName, refUpdate.getRef().getName());
      if (refEnforcement.getPolicy(projectName) == EnforcePolicy.REQUIRED) {
        throw e;
      }
    }
    return null;
  }

  protected void executeBatchUpdateWithPolicy(
      String errorMessage,
      BatchValidationWrapper delegateValidation,
      BatchRefUpdate batchRefUpdate,
      NoParameterVoidFunction gitUpdateFun)
      throws IOException {
    if (refEnforcement.getPolicy(projectName) == EnforcePolicy.IGNORED) {
      gitUpdateFun.invoke();
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

  private <T extends Throwable> void failWith(T e, EnforcePolicy policy) throws T {
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

  protected RefUpdate.Result doExecuteRefUpdate(
      RefUpdate refUpdate, NoParameterFunction<RefUpdate.Result> refUpdateFunction)
      throws IOException {
    try (CloseableSet<AutoCloseable> locks = new CloseableSet<>()) {
      assertRefPairsAreInSyncWithSharedDb(ImmutableList.of(newRefPairFrom(refUpdate)), locks);
      RefUpdate.Result result = refUpdateFunction.invoke();
      if (isSuccessful(result)) {
        updateSharedDbOrThrowExceptionFor(newRefPairFrom(refUpdate));
      }
      return result;
    }
  }

  protected void updateSharedDbOrThrowExceptionFor(RefPair successfulRefPair) throws IOException {
    // We are not checking refs that should be ignored
    final EnforcePolicy refEnforcementPolicy =
        refEnforcement.getPolicy(projectName, successfulRefPair.getName());
    if (refEnforcementPolicy == EnforcePolicy.IGNORED) return;

    String errorMessage =
        String.format(
            "Not able to persist the data in Zookeeper for project '%s' and ref '%s',"
                + "the cluster is now in Split Brain since the commit has been "
                + "persisted locally but not in SharedRef the value %s",
            projectName, successfulRefPair.getName(), successfulRefPair.newRef.getObjectId());
    boolean succeeded;
    try {
      succeeded =
          sharedRefDb.compareAndPut(
              projectName, successfulRefPair.oldRef, successfulRefPair.newRef);
    } catch (IOException e) {
      throw new SharedDbSplitBrainException(errorMessage, e);
    }

    if (!succeeded) {
      throw new SharedDbSplitBrainException(errorMessage);
    }
  }

  protected void assertRefPairsAreInSyncWithSharedDb(
      List<RefPair> refsToUpdate, CloseableSet<AutoCloseable> locks)
      throws SharedLockException, OutOfSyncException, IOException {
    for (RefPair refPair : refsToUpdate) {
      Ref localRef = refDb.getRef(refPair.getName());

      final EnforcePolicy refEnforcementPolicy =
          refEnforcement.getPolicy(projectName, refPair.getName());
      if (refEnforcementPolicy == EnforcePolicy.IGNORED) continue;

      String resourceLockKey = String.format("%s-%s", projectName, refPair.getName());
      locks.addResourceIfNotExist(
          resourceLockKey, () -> sharedRefDb.lockRef(projectName, refPair.getName()));

      boolean isInSync;
      if (localRef != null) {
        isInSync = sharedRefDb.isUpToDate(projectName, localRef);
      } else {
        isInSync = !sharedRefDb.exists(projectName, refPair.getName());
      }

      if (!isInSync) {
        failWith(new OutOfSyncException(projectName, refPair.oldRef), refEnforcementPolicy);
      }
    }
  }

  protected boolean isSuccessful(RefUpdate.Result result) {
    switch (result) {
      case NEW:
      case FORCED:
      case FAST_FORWARD:
      case NO_CHANGE:
      case RENAMED:
        return true;

      case REJECTED_OTHER_REASON:
      case REJECTED_MISSING_OBJECT:
      case REJECTED_CURRENT_BRANCH:
      case NOT_ATTEMPTED:
      case LOCK_FAILURE:
      case IO_FAILURE:
      case REJECTED:
      default:
        return false;
    }
  }

  protected RefPair newRefPairFrom(RefUpdate refUpdate) {
    return new RefPair(
        refUpdate.getRef(), sharedRefDb.newRef(refUpdate.getName(), refUpdate.getNewObjectId()));
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
        String key, ExceptionThrowingSupplier<T, SharedLockException> resourceFactory)
        throws SharedLockException {
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

  public interface RefValidationWrapper {
    RefUpdate.Result apply(NoParameterFunction<RefUpdate.Result> arg, RefUpdate refUpdate)
        throws IOException;
  }

  public class SharedDbSplitBrainException extends IOException {
    private static final long serialVersionUID = 1L;

    public SharedDbSplitBrainException(String message) {
      super(message);
    }

    public SharedDbSplitBrainException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  interface NoParameterFunction<T> {
    T invoke() throws IOException;
  }

  interface NoParameterVoidFunction {
    void invoke() throws IOException;
  }
}
