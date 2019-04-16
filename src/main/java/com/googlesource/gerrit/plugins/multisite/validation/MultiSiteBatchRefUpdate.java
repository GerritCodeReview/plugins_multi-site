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

import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.eclipse.jgit.util.time.ProposedTimestamp;

public class MultiSiteBatchRefUpdate extends BatchRefUpdate {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final BatchRefUpdate batchRefUpdate;
  private final RefDatabase refDb;
  private final SharedRefDatabase<AutoCloseable> sharedRefDb;
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
      SharedRefDatabase<AutoCloseable> sharedRefDb, @Assisted String projectName, @Assisted RefDatabase refDb) {
    super(refDb);

    this.sharedRefDb = sharedRefDb;
    this.projectName = projectName;
    this.refDb = refDb;
    this.batchRefUpdate = refDb.newBatchUpdate();
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
    executeWrapper(walk, monitor, options);
  }

  @Override
  public void execute(RevWalk walk, ProgressMonitor monitor) throws IOException {
    executeWrapper(walk, monitor, Collections.EMPTY_LIST);
  }

  @Override
  public String toString() {
    return batchRefUpdate.toString();
  }

  private void updateSharedRefDb(
      Stream<RefPair> oldRefs, RevWalk walk, ProgressMonitor monitor, List<String> options)
      throws Exception {
    List<RefPair> refsToUpdate =
        oldRefs.sorted(comparing(RefPair::hasFailed).reversed()).collect(Collectors.toList());

    if (refsToUpdate.isEmpty()) {
      return;
    }

    if (refsToUpdate.get(0).hasFailed()) {
      RefPair failedRef = refsToUpdate.get(0);
      throw new IOException(
          "Failed to fetch ref entries" + failedRef.newRef.getName(), failedRef.exception);
    }


    try(CloseableList<AutoCloseable> locks = new CloseableList()) {
      assertBatchCommandsAreInSync(refsToUpdate, locks);

      if (options.isEmpty()) {
        batchRefUpdate.execute(walk, monitor);
      } else {
        batchRefUpdate.execute(walk, monitor, options);
      }

      updateSharedDBForSuccessfulCommands();
    } catch (Exception e) {
      logger.atWarning().log("Failed to apply full batch %s", e.getMessage());
    }
  }

  private void updateSharedDBForSuccessfulCommands() throws IOException {
    List<RefPair> successfulRefPairs =
        batchRefUpdate.getCommands().stream()
            .filter(cmd -> cmd.getResult() == Result.OK)
            .map(
                cmd ->
                    new RefPair(
                        cmd.getOldId() == null
                            ? sharedRefDb.NULL_REF
                            : sharedRefDb.newRef(cmd.getRefName(), cmd.getOldId()),
                        sharedRefDb.newRef(cmd.getRefName(), cmd.getNewId())))
            .collect(Collectors.toList());

    for (RefPair successfulRefPair : successfulRefPairs) {
      sharedRefDb.compareAndPut(
          projectName, successfulRefPair.oldRef, successfulRefPair.newRef);
    }
  }

  private void assertBatchCommandsAreInSync(List<RefPair> refsToUpdate,
      CloseableList<AutoCloseable> locks) throws Exception {
    for (RefPair refPair : refsToUpdate) {
      locks.addResource(sharedRefDb.lockRef(projectName, refPair.oldRef));

      if (!sharedRefDb.isMostRecentVersion(projectName, refPair.oldRef)) {
        String errorMessage =
            String.format(
                "Ref %s not in sync with sharedDb, aborting batch", refPair.oldRef.getName());

        logger.atWarning().log(errorMessage);
        throw new Exception(errorMessage);
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

  private void executeWrapper(RevWalk walk, ProgressMonitor monitor, List<String> options)
      throws IOException {
    try {
      updateSharedRefDb(getRefsPairs(batchRefUpdate.getCommands()), walk, monitor, options);
    } catch (Exception e) {
      String errorMessage =
          String.format(
              "Failing batch executeWrapper in MultiSiteBatchRefUpdate with exception %s",
              e.getMessage());
      logger.atWarning().log(errorMessage);
      throw new IOException(errorMessage);
    }
  }

  private Ref getNewRef(ReceiveCommand command) {
    return sharedRefDb.newRef(command.getRefName(), command.getNewId());
  }

  public static class CloseableList<T extends AutoCloseable> implements AutoCloseable {
    private final List<T> elements;

    public CloseableList() {
      this(new ArrayList<>());
    }

    public CloseableList(List<T> elements) {
      this.elements = elements;
    }

    public void addResource(T resource) {
      elements.add(resource);
    }

    @Override
    public void close() throws Exception {

    }
  }
}
