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

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.multisite.validation.RefUpdateValidator.NoParameterFunction;
import java.io.IOException;
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

  protected final RefUpdate refUpdateBase;
  private final String projectName;
  private final RefUpdateValidator.Factory refValidatorFactory;
  private final RefUpdateValidator refUpdateValidator;

  public interface Factory {
    MultiSiteRefUpdate create(String projectName, RefUpdate refUpdate, RefDatabase refDb);
  }

  @Inject
  public MultiSiteRefUpdate(
      RefUpdateValidator.Factory refValidatorFactory,
      @Assisted String projectName,
      @Assisted RefUpdate refUpdate,
      @Assisted RefDatabase refDb) {
    super(refUpdate.getRef());
    refUpdateBase = refUpdate;
    this.projectName = projectName;
    this.refValidatorFactory = refValidatorFactory;
    refUpdateValidator = this.refValidatorFactory.create(this.projectName, refDb);
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
    return refUpdateValidator.executeRefUpdate(
        refUpdateBase,
        refUpdateBase::update,
        objectId -> rollback(objectId, refUpdateBase::update));
  }

  @Override
  public Result update(RevWalk rev) throws IOException {
    return refUpdateValidator.executeRefUpdate(
        refUpdateBase,
        () -> refUpdateBase.update(rev),
        objectId -> rollback(objectId, () -> refUpdateBase.update(rev)));
  }

  @Override
  public Result delete() throws IOException {
    return refUpdateValidator.executeRefUpdate(
        refUpdateBase,
        refUpdateBase::delete,
        objectId -> rollback(objectId, refUpdateBase::update));
  }

  @Override
  public Result delete(RevWalk walk) throws IOException {
    return refUpdateValidator.executeRefUpdate(
        refUpdateBase,
        () -> refUpdateBase.delete(walk),
        objectId -> rollback(objectId, () -> refUpdateBase.update(walk)));
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
    return refUpdateValidator.executeRefUpdate(
        refUpdateBase,
        refUpdateBase::forceUpdate,
        objectId -> rollback(objectId, refUpdateBase::forceUpdate));
  }

  @Override
  public Result link(String target) throws IOException {
    return refUpdateBase.link(target);
  }

  @Override
  public void setCheckConflicting(boolean check) {
    refUpdateBase.setCheckConflicting(check);
  }

  private Result rollback(ObjectId objectId, NoParameterFunction<Result> updateFunction)
      throws IOException {
    refUpdateBase.setExpectedOldObjectId(refUpdateBase.getNewObjectId());
    refUpdateBase.setNewObjectId(objectId);
    return updateFunction.invoke();
  }
}
