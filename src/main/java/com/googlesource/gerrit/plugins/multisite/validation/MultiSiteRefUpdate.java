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
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import java.io.IOException;
import java.util.function.Supplier;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushCertificate;

public class MultiSiteRefUpdate extends RefUpdate {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected final RefUpdate refUpdateBase;

  private final SharedRefDatabase sharedDb;
  private final String projectName;

  public interface Factory {
    MultiSiteRefUpdate create(String projectName, RefUpdate refUpdate);
  }

  @Inject
  public MultiSiteRefUpdate(
      SharedRefDatabase db, @Assisted String projectName, @Assisted RefUpdate refUpdate) {
    super(refUpdate.getRef());
    refUpdateBase = refUpdate;
    this.sharedDb = db;
    this.projectName = projectName;
  }

  private void checkSharedDBForRefUpdate() throws IOException {
    if (isANewRef()) {
      if (sharedDb.exists(projectName, refUpdateBase.getName()))
        throw new IOException(
            String.format(
                "Unable to create ref '%s', trying to create a new ref but there is a value "
                    + "already in the shared ref db",
                refUpdateBase.getName()));
    }
    if (!sharedDb.isMostRecentRefVersion(projectName, refUpdateBase.getRef()))
      throw new IOException(
          String.format(
              "Unable to update ref '%s', the local objectId '%s' is not equal to the one "
                  + "in the shared ref datasuper",
              refUpdateBase.getName(), refUpdateBase.getOldObjectId()));
  }

  private boolean isANewRef() {
    return refUpdateBase.getRef().getObjectId() == null
        || refUpdateBase.getRef().getObjectId().equals(ObjectId.zeroId());
  }

  private void checkSharedDbForRefDelete() throws IOException {
    Ref oldRef = this.getRef();
    try {
      if (!sharedDb.compareAndRemove(projectName, oldRef)) {
        throw new IOException(
            String.format(
                "Unable to delete ref '%s', the local ObjectId '%s' is not equal to the one "
                    + "in the shared ref database",
                oldRef.getName(), oldRef.getName()));
      }
    } catch (IOException ioe) {
      logger.atSevere().withCause(ioe).log(
          "Local status inconsistent with shared ref database for ref %s. "
              + "Trying to delete it but it is not in the DB",
          oldRef.getName());

      throw new IOException(
          String.format(
              "Unable to delete ref '%s', cannot find it in the shared ref database",
              oldRef.getName()),
          ioe);
    }
  }

  @Override
  protected RefDatabase getRefDatabase() {
    return notImplementedException();
  }

  private <T> T notImplementedException() {
    throw new IllegalStateException("This method should have never been invoked");
  }

  @Override
  protected Repository getRepository() {
    return notImplementedException();
  }

  @Override
  protected boolean tryLock(boolean deref) throws IOException {
    return notImplementedException();
  }

  @Override
  protected void unlock() {
    notImplementedException();
  }

  @Override
  protected Result doUpdate(Result result) throws IOException {
    return notImplementedException();
  }

  @Override
  protected Result doDelete(Result result) throws IOException {
    return notImplementedException();
  }

  @Override
  protected Result doLink(String target) throws IOException {
    return notImplementedException();
  }

  @Override
  public Result update() throws IOException {
    return updateIfInSyncWithRefDb(
        () -> refUpdateBase.update(),
        () -> {
          Ref newRef = sharedDb.newRef(refUpdateBase.getName(), refUpdateBase.getNewObjectId());
          return sharedDb.compareAndPut(projectName, refUpdateBase.getRef(), newRef);
        });
  }

  private boolean isSuccessful(Result result) {
    switch (result) {
      case NEW:
      case FORCED:
      case FAST_FORWARD:
      case NO_CHANGE:
        return true;

      default:
        return false;
    }
  }

  @Override
  public Result update(RevWalk rev) throws IOException {
    return updateIfInSyncWithRefDb(
        () -> refUpdateBase.update(rev),
        () -> {
          Ref newRef = sharedDb.newRef(refUpdateBase.getName(), refUpdateBase.getNewObjectId());
          return sharedDb.compareAndPut(projectName, refUpdateBase.getRef(), newRef);
        });
  }

  private Result updateIfInSyncWithRefDb(
      NoParameterFunction<Result> delegateUpdate, NoParameterFunction<Boolean> postUpdateSharedDb)
      throws IOException {
    try {
      try (AutoCloseable lock = sharedDb.lockRef(projectName, refUpdateBase.getRef())) {
        checkSharedDBForRefUpdate();
        Result result = delegateUpdate.apply();
        if (isSuccessful(result)) {
          performPostUpdate(postUpdateSharedDb);
        }
        return result;
      }
    } catch (IOException toBeRethrown) {
      throw toBeRethrown;
    } catch (Exception autoCloseableException) {
      logger.atWarning().withCause(autoCloseableException).log(
          "Failure trying to close lock with shared DB");
      throw new IOException("Failure trying to close lock with shared DB", autoCloseableException);
    }
  }

  private void performPostUpdate(NoParameterFunction<Boolean> postUpdateSharedDb)
      throws IOException {
    final Supplier<String> errorMessage =
        () ->
            String.format(
                "Not able to update the database, trying to update Ref %s with new objectId %s"
                    + "Local Git repository was updated but not able to update zookeeper."
                    + "Gerrit instance in a split-brain.",
                refUpdateBase.getName(), refUpdateBase.getNewObjectId());

    boolean postUpdateSuccess;
    try {
      // If any failure happens here we are in a split-brain situation.
      // We must add a failure counter to be monitored.
      postUpdateSuccess = postUpdateSharedDb.apply();
    } catch (IOException updateException) {
      logger.atWarning().log(errorMessage.get());
      throw new IOException(errorMessage.get(), updateException);
    }

    if (!postUpdateSuccess) {
      logger.atWarning().log(errorMessage.get());
      throw new IOException(errorMessage.get());
    }
  }

  @Override
  public Result delete() throws IOException {
    return updateIfInSyncWithRefDb(
        () -> refUpdateBase.delete(),
        () -> {
          return sharedDb.compareAndRemove(projectName, refUpdateBase.getRef());
        });
  }

  @Override
  public Result delete(RevWalk walk) throws IOException {
    return updateIfInSyncWithRefDb(
        () -> refUpdateBase.delete(walk),
        () -> {
          return sharedDb.compareAndRemove(projectName, refUpdateBase.getRef());
        });
  }

  @Override
  public int hashCode() {
    return refUpdateBase.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return refUpdateBase.equals(obj);
  }

  @Override
  public String toString() {
    return refUpdateBase.toString();
  }

  @Override
  public String getName() {
    return refUpdateBase.getName();
  }

  @Override
  public Ref getRef() {
    return refUpdateBase.getRef();
  }

  @Override
  public ObjectId getNewObjectId() {
    return refUpdateBase.getNewObjectId();
  }

  @Override
  public void setDetachingSymbolicRef() {
    refUpdateBase.setDetachingSymbolicRef();
  }

  @Override
  public boolean isDetachingSymbolicRef() {
    return refUpdateBase.isDetachingSymbolicRef();
  }

  @Override
  public void setNewObjectId(AnyObjectId id) {
    refUpdateBase.setNewObjectId(id);
  }

  @Override
  public ObjectId getExpectedOldObjectId() {
    return refUpdateBase.getExpectedOldObjectId();
  }

  @Override
  public void setExpectedOldObjectId(AnyObjectId id) {
    refUpdateBase.setExpectedOldObjectId(id);
  }

  @Override
  public boolean isForceUpdate() {
    return refUpdateBase.isForceUpdate();
  }

  @Override
  public void setForceUpdate(boolean b) {
    refUpdateBase.setForceUpdate(b);
  }

  @Override
  public PersonIdent getRefLogIdent() {
    return refUpdateBase.getRefLogIdent();
  }

  @Override
  public void setRefLogIdent(PersonIdent pi) {
    refUpdateBase.setRefLogIdent(pi);
  }

  @Override
  public String getRefLogMessage() {
    return refUpdateBase.getRefLogMessage();
  }

  @Override
  public void setRefLogMessage(String msg, boolean appendStatus) {
    refUpdateBase.setRefLogMessage(msg, appendStatus);
  }

  @Override
  public void disableRefLog() {
    refUpdateBase.disableRefLog();
  }

  @Override
  public void setForceRefLog(boolean force) {
    refUpdateBase.setForceRefLog(force);
  }

  @Override
  public ObjectId getOldObjectId() {
    return refUpdateBase.getOldObjectId();
  }

  @Override
  public void setPushCertificate(PushCertificate cert) {
    refUpdateBase.setPushCertificate(cert);
  }

  @Override
  public Result getResult() {
    return refUpdateBase.getResult();
  }

  @Override
  public Result forceUpdate() throws IOException {
    return refUpdateBase.forceUpdate();
  }

  @Override
  public Result link(String target) throws IOException {
    return refUpdateBase.link(target);
  }

  @Override
  public void setCheckConflicting(boolean check) {
    refUpdateBase.setCheckConflicting(check);
  }

  interface NoParameterFunction<T> {
    T apply() throws IOException;
  }
}
