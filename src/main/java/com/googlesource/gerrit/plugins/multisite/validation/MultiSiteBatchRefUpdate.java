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

import static java.util.Comparator.comparing;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.time.ProposedTimestamp;

public class MultiSiteBatchRefUpdate extends BatchRefUpdate {
  private final BatchRefUpdate batchRefUpdate;
  private final RefDatabase refDb;
  private final SharedRefDatabase sharedRefDb;
  private final List<ReceiveCommand> receiveCommands;
  private final String projectName;

  public static class RefPair {
    final Ref oldRef;
    final Ref newRef;
    final Exception exception;

    RefPair(Ref oldRef, Ref newRef) {
      this.oldRef = oldRef;
      this.newRef = newRef;
      this.exception = null;
    }

    RefPair(Ref newRef, Exception e) {
      this.newRef = newRef;
      this.oldRef = SharedRefDatabase.NULL_REF;
      this.exception = e;
    }

    public boolean hasFailed() {
      return exception != null;
    }
  }

  public static interface Factory {
    MultiSiteBatchRefUpdate create(String projectName, RefDatabase refDb);
  }

  @Inject
  public MultiSiteBatchRefUpdate(
      SharedRefDatabase sharedRefDb, @Assisted String projectName, @Assisted RefDatabase refDb) {
    super(refDb);

    this.sharedRefDb = sharedRefDb;
    this.projectName = projectName;
    this.refDb = refDb;
    this.batchRefUpdate = refDb.newBatchUpdate();
    this.receiveCommands = new ArrayList<>();
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
    return batchRefUpdate.setAllowNonFastForwards(allow);
  }

  @Override
  public PersonIdent getRefLogIdent() {
    return batchRefUpdate.getRefLogIdent();
  }

  @Override
  public BatchRefUpdate setRefLogIdent(PersonIdent pi) {
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
    return batchRefUpdate.setRefLogMessage(msg, appendStatus);
  }

  @Override
  public BatchRefUpdate disableRefLog() {
    return batchRefUpdate.disableRefLog();
  }

  @Override
  public BatchRefUpdate setForceRefLog(boolean force) {
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
    receiveCommands.add(cmd);
    return batchRefUpdate.addCommand(cmd);
  }

  @Override
  public BatchRefUpdate addCommand(ReceiveCommand... cmd) {
    for (ReceiveCommand receiveCommand : cmd) {
      receiveCommands.add(receiveCommand);
    }
    return batchRefUpdate.addCommand(cmd);
  }

  @Override
  public BatchRefUpdate addCommand(Collection<ReceiveCommand> cmd) {
    receiveCommands.addAll(cmd);
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
    updateSharedRefDb(getRefsPairs());
    batchRefUpdate.execute(walk, monitor, options);
  }

  @Override
  public void execute(RevWalk walk, ProgressMonitor monitor) throws IOException {
    updateSharedRefDb(getRefsPairs());
    batchRefUpdate.execute(walk, monitor);
  }

  @Override
  public String toString() {
    return batchRefUpdate.toString();
  }

  private void updateSharedRefDb(Stream<RefPair> oldRefs) throws IOException {
    List<RefPair> refsToUpdate =
        oldRefs.sorted(comparing(RefPair::hasFailed).reversed()).collect(Collectors.toList());
    if (refsToUpdate.get(0).hasFailed()) {
      RefPair failedRef = refsToUpdate.get(0);
      throw new IOException(
          "Failed to fetch ref entries" + failedRef.newRef.getName(), failedRef.exception);
    }

    for (RefPair refPair : refsToUpdate) {
      boolean compareAndPutResult =
          sharedRefDb.compareAndPut(projectName, refPair.oldRef, refPair.newRef);
      if (!compareAndPutResult) {
        throw new IOException(
            String.format(
                "This repos is out of sync for project %s. old_ref=%s, new_ref=%s",
                projectName, refPair.oldRef, refPair.newRef));
      }
    }
  }

  private Stream<RefPair> getRefsPairs() {
    return receiveCommands.stream().map(this::getRefPairForCommand);
  }

  @VisibleForTesting
  private Ref returnOldChangeRefForNewChangeRef(Ref newRef) throws IOException {
    String refName = newRef.getName();
    if (refName.contains("change")) {
      if (!refName.contains("meta")) {
        String[] refNameSplit = refName.split("/");
        Integer patchsetNumber = new Integer(refNameSplit[refNameSplit.length - 1]);
        if (patchsetNumber > 1) {
          String patchsetRelativePathRegex = "/" + patchsetNumber + "$";
          String prevPatchsetRelativePath = "/" + (patchsetNumber - 1);
          String previousPatchSetPath =
              refName.replaceAll(patchsetRelativePathRegex, prevPatchsetRelativePath);

          return refDb.getRef(previousPatchSetPath);
        }
      }
    }
    return SharedRefDatabase.NULL_REF;
  }

  private RefPair getRefPairForCommand(ReceiveCommand command) {
    try {
      switch (command.getType()) {
        case CREATE:
          Ref newRef = getNewRef(command);
          Ref oldRef = returnOldChangeRefForNewChangeRef(newRef);
          return new RefPair(oldRef, newRef);

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
}
