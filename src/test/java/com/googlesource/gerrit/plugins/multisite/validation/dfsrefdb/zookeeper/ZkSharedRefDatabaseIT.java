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

package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.googlesource.gerrit.plugins.multisite.validation.BatchRefUpdateValidator;
import com.googlesource.gerrit.plugins.multisite.validation.MultiSiteBatchRefUpdate;
import com.googlesource.gerrit.plugins.multisite.validation.ValidationMetrics;
import com.googlesource.gerrit.plugins.multisite.validation.ZkConnectionConfig;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DefaultSharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement;
import org.apache.curator.retry.RetryNTimes;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.eclipse.jgit.transport.ReceiveCommand.Type;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

@UseLocalDisk
public class ZkSharedRefDatabaseIT extends AbstractDaemonTest implements RefFixture {
  @Rule public TestName nameRule = new TestName();

  ZookeeperTestContainerSupport zookeeperContainer;
  ZkSharedRefDatabase zkSharedRefDatabase;
  SharedRefEnforcement refEnforcement;

  int SLEEP_BETWEEN_RETRIES_MS = 30;
  long TRANSACTION_LOCK_TIMEOUT = 1000l;
  int NUMBER_OF_RETRIES = 5;

  @Before
  public void setup() {
    refEnforcement = new DefaultSharedRefEnforcement();
    zookeeperContainer = new ZookeeperTestContainerSupport(false);
    zkSharedRefDatabase =
        new ZkSharedRefDatabase(
            zookeeperContainer.getCurator(),
            new ZkConnectionConfig(
                new RetryNTimes(NUMBER_OF_RETRIES, SLEEP_BETWEEN_RETRIES_MS),
                TRANSACTION_LOCK_TIMEOUT));
  }

  @After
  public void cleanup() {
    zookeeperContainer.cleanup();
  }

  @Test
  public void sequenceOfGitUpdatesWithARejectionCausesZKCheckToFail() throws Exception {
    ObjectId commitObjectIdOne = commitBuilder().add("test_file.txt", "A").create().getId();
    ObjectId commitObjectIdTwo = commitBuilder().add("test_file.txt", "B").create().getId();
    ObjectId commitObjectIdThree = commitBuilder().add("test_file.txt", "A2").create().getId();

    ReceiveCommand aRefCreation =
        new ReceiveCommand(ObjectId.zeroId(), commitObjectIdOne, A_TEST_REF_NAME);

    ReceiveCommand aCommandThatWillBeRejectedByJGit =
        new ReceiveCommand(
            commitObjectIdOne, commitObjectIdTwo, A_TEST_REF_NAME, Type.UPDATE_NONFASTFORWARD);

    ReceiveCommand aCommandThatShouldSucceed =
        new ReceiveCommand(commitObjectIdOne, commitObjectIdThree, A_TEST_REF_NAME, Type.UPDATE);

    InMemoryRepository repository = testRepo.getRepository();
    try (RevWalk rw = new RevWalk(repository)) {
      newBatchRefUpdate(repository, aRefCreation).execute(rw, NullProgressMonitor.INSTANCE);

      // The rejection of this command should not leave the shared DB into an inconsistent state
      newBatchRefUpdate(repository, aCommandThatWillBeRejectedByJGit)
          .execute(rw, NullProgressMonitor.INSTANCE);

      // This command will succeed only if the previous one is not leaving any traces in the
      // shared ref DB
      newBatchRefUpdate(repository, aCommandThatShouldSucceed)
          .execute(rw, NullProgressMonitor.INSTANCE);

      assertThat(aRefCreation.getResult()).isEqualTo(Result.OK);
      assertThat(aCommandThatWillBeRejectedByJGit.getResult())
          .isEqualTo(Result.REJECTED_NONFASTFORWARD);
      assertThat(aCommandThatShouldSucceed.getResult()).isEqualTo(Result.OK);
    }
  }

  @Test
  public void sequenceOfGitUpdatesWithMultipleCommandsInBatch() throws Exception {
	ObjectId commitObjectIdOne = commitBuilder().add("test_file1.txt", "A").create().getId();
    ObjectId commitObjectIdTwo = commitBuilder().add("test_file1.txt", "B").create().getId();
    ObjectId commitObjectIdThree = commitBuilder().add("test_file2.txt", "C").create().getId();

    ReceiveCommand firstCommand =
        new ReceiveCommand(ObjectId.zeroId(), commitObjectIdOne, A_TEST_REF_NAME);

    ReceiveCommand secondCommand =
        new ReceiveCommand(ObjectId.zeroId(), commitObjectIdTwo, A_TEST_REF_NAME);

    ReceiveCommand aNewCreate =
        new ReceiveCommand(ObjectId.zeroId(), commitObjectIdThree, "refs/for/master2");

    InMemoryRepository repository = testRepo.getRepository();
    try (RevWalk rw = new RevWalk(repository)) {
      newBatchRefUpdate(repository, firstCommand, secondCommand, aNewCreate)
          .execute(rw, NullProgressMonitor.INSTANCE);
    }

    // All commands in batch failed because of the second one
    assertThat(firstCommand.getResult()).isEqualTo(Result.OK);
    assertThat(secondCommand.getResult()).isEqualTo(Result.OK);
    assertThat(aNewCreate.getResult()).isEqualTo(Result.OK);

    assertTrue(existsDataInZkForCommand(secondCommand));
    assertTrue(existsDataInZkForCommand(aNewCreate));
  }

  @Test
  public void aBatchWithOneFailedCommandShouldFailAllOtherCommands() throws Exception {
    ObjectId commitObjectIdOne = commitBuilder().add("test_file1.txt", "A").create().getId();
    ObjectId commitObjectIdTwo = commitBuilder().add("test_file1.txt", "B").create().getId();
    ObjectId commitObjectIdThree = commitBuilder().add("test_file2.txt", "C").create().getId();

    ReceiveCommand firstCommand =
        new ReceiveCommand(ObjectId.zeroId(), commitObjectIdOne, A_TEST_REF_NAME);

    ReceiveCommand aNonFastForwardUpdate =
        new ReceiveCommand(
            commitObjectIdOne, commitObjectIdTwo, A_TEST_REF_NAME, Type.UPDATE_NONFASTFORWARD);

    ReceiveCommand aNewCreate =
        new ReceiveCommand(ObjectId.zeroId(), commitObjectIdThree, "refs/for/master2");

    InMemoryRepository repository = testRepo.getRepository();
    try (RevWalk rw = new RevWalk(repository)) {
      newBatchRefUpdate(repository, firstCommand, aNonFastForwardUpdate, aNewCreate)
          .execute(rw, NullProgressMonitor.INSTANCE);
    }

    // All commands in batch failed because of the second one
    assertThat(firstCommand.getResult()).isEqualTo(Result.REJECTED_OTHER_REASON);
    assertThat(aNonFastForwardUpdate.getResult()).isEqualTo(Result.REJECTED_NONFASTFORWARD);
    assertThat(aNewCreate.getResult()).isEqualTo(Result.REJECTED_OTHER_REASON);

    // Zookeeper has been left untouched
    assertFalse(existsDataInZkForCommand(firstCommand));
    assertFalse(existsDataInZkForCommand(aNonFastForwardUpdate));
    assertFalse(existsDataInZkForCommand(aNewCreate));
  }

  private boolean existsDataInZkForCommand(ReceiveCommand firstCommand) throws Exception {
    return zkSharedRefDatabase.exists(project.get(), firstCommand.getRefName());
  }

  private MultiSiteBatchRefUpdate newBatchRefUpdate(
      Repository localGitRepo, ReceiveCommand... commands) {

    BatchRefUpdateValidator.Factory batchRefValidatorFactory =
        new BatchRefUpdateValidator.Factory() {
          @Override
          public BatchRefUpdateValidator create(String projectName, RefDatabase refDb) {
            return new BatchRefUpdateValidator(
                zkSharedRefDatabase,
                new ValidationMetrics(new DisabledMetricMaker()),
                new DefaultSharedRefEnforcement(),
                projectName,
                refDb);
          }
        };

    MultiSiteBatchRefUpdate result =
        new MultiSiteBatchRefUpdate(
            batchRefValidatorFactory, project.get(), localGitRepo.getRefDatabase());

    result.setAllowNonFastForwards(false);
    for (ReceiveCommand command : commands) {
      result.addCommand(command);
    }
    return result;
  }

  @Override
  public String testBranch() {
    return "branch_" + nameRule.getMethodName();
  }
}
