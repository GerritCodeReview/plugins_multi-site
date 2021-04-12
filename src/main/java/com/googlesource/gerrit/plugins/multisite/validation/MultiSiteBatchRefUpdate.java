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
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.time.ProposedTimestamp;

public class MultiSiteBatchRefUpdate extends BatchRefUpdate {

  private final BatchRefUpdate batchRefUpdate;
  private final BatchRefUpdate batchRefUpdateRollback;
  private final String project;
  private final BatchRefUpdateValidator.Factory batchRefValidatorFactory;
  private final RefDatabase refDb;

  public static interface Factory {
    MultiSiteBatchRefUpdate create(String project, RefDatabase refDb);
  }

  @Inject
  public MultiSiteBatchRefUpdate(
      BatchRefUpdateValidator.Factory batchRefValidatorFactory,
      @Assisted String project,
      @Assisted RefDatabase refDb) {
    super(refDb);
    this.refDb = refDb;
    this.project = project;
    this.batchRefUpdate = refDb.newBatchUpdate();
    this.batchRefUpdateRollback = refDb.newBatchUpdate();
    this.batchRefValidatorFactory = batchRefValidatorFactory;
  }

  @Override
  public int hashCode() {
    return batchRefUpdate.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return batchRefUpdate.equals(obj);
  }

  @Override
  public boolean isAllowNonFastForwards() {
    return batchRefUpdate.isAllowNonFastForwards();
  }

  @Override
  public BatchRefUpdate setAllowNonFastForwards(boolean allow) {
    batchRefUpdateRollback.setAllowNonFastForwards(allow);
    return batchRefUpdate.setAllowNonFastForwards(allow);
  }

  @Override
  public PersonIdent getRefLogIdent() {
    return batchRefUpdate.getRefLogIdent();
  }

  @Override
  public BatchRefUpdate setRefLogIdent(PersonIdent pi) {
    batchRefUpdateRollback.setRefLogIdent(pi);
    return batchRefUpdate.setRefLogIdent(pi);
  }

  @Override
  public String getRefLogMessage() {
    return batchRefUpdate.getRefLogMessage();
  }

  @Override
  public boolean isRefLogIncludingResult() {
    return batchRefUpdate.isRefLogIncludingResult();
  }

  @Override
  public BatchRefUpdate setRefLogMessage(String msg, boolean appendStatus) {
    batchRefUpdateRollback.setRefLogMessage(msg, appendStatus);
    return batchRefUpdate.setRefLogMessage(msg, appendStatus);
  }

  @Override
  public BatchRefUpdate disableRefLog() {
    return batchRefUpdate.disableRefLog();
  }

  @Override
  public BatchRefUpdate setForceRefLog(boolean force) {
    batchRefUpdateRollback.setForceRefLog(force);
    return batchRefUpdate.setForceRefLog(force);
  }

  @Override
  public boolean isRefLogDisabled() {
    return batchRefUpdate.isRefLogDisabled();
  }

  @Override
  public BatchRefUpdate setAtomic(boolean atomic) {
    return batchRefUpdate.setAtomic(atomic);
  }

  @Override
  public boolean isAtomic() {
    return batchRefUpdate.isAtomic();
  }

  @Override
  public void setPushCertificate(PushCertificate cert) {
    batchRefUpdate.setPushCertificate(cert);
  }

  @Override
  public List<ReceiveCommand> getCommands() {
    return batchRefUpdate.getCommands();
  }

  @Override
  public BatchRefUpdate addCommand(ReceiveCommand cmd) {
    return batchRefUpdate.addCommand(cmd);
  }

  @Override
  public BatchRefUpdate addCommand(ReceiveCommand... cmd) {
    return batchRefUpdate.addCommand(cmd);
  }

  @Override
  public BatchRefUpdate addCommand(Collection<ReceiveCommand> cmd) {
    return batchRefUpdate.addCommand(cmd);
  }

  @Override
  public List<String> getPushOptions() {
    return batchRefUpdate.getPushOptions();
  }

  @Override
  public List<ProposedTimestamp> getProposedTimestamps() {
    return batchRefUpdate.getProposedTimestamps();
  }

  @Override
  public BatchRefUpdate addProposedTimestamp(ProposedTimestamp ts) {
    return batchRefUpdate.addProposedTimestamp(ts);
  }

  @Override
  public void execute(RevWalk walk, ProgressMonitor monitor, List<String> options)
      throws IOException {
    batchRefValidatorFactory
        .create(project, refDb)
        .executeBatchUpdateWithValidation(
            batchRefUpdate,
            () -> batchRefUpdate.execute(walk, monitor, options),
            (commands) ->
                batchRefUpdateRollback.addCommand(commands).execute(walk, monitor, options));
  }

  @Override
  public void execute(RevWalk walk, ProgressMonitor monitor) throws IOException {
    batchRefValidatorFactory
        .create(project, refDb)
        .executeBatchUpdateWithValidation(
            batchRefUpdate,
            () -> batchRefUpdate.execute(walk, monitor),
            (commands) -> batchRefUpdateRollback.addCommand(commands).execute(walk, monitor));
  }

  @Override
  public String toString() {
    return batchRefUpdate.toString();
  }
}
