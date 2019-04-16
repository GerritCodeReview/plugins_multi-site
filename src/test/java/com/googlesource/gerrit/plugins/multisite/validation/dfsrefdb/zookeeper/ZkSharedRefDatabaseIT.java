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

package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.googlesource.gerrit.plugins.multisite.validation.MultiSiteBatchRefUpdate;
import org.apache.curator.retry.RetryNTimes;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
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

public class ZkSharedRefDatabaseIT extends AbstractDaemonTest implements RefFixture {
  @Rule public TestName nameRule = new TestName();

  ZookeeperTestContainerSupport zookeeperContainer;
  ZkSharedRefDatabase zkSharedRefDatabase;

  @Before
  public void setup() {
    zookeeperContainer = new ZookeeperTestContainerSupport(false);
    zkSharedRefDatabase =
        new ZkSharedRefDatabase(zookeeperContainer.getCurator(), new RetryNTimes(5, 30), 1000l);
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

    ReceiveCommand firstCommand =
        new ReceiveCommand(ObjectId.zeroId(), commitObjectIdOne, A_TEST_REF_NAME);

    ReceiveCommand aNonFastForwardUpdate =
        new ReceiveCommand(
            commitObjectIdOne, commitObjectIdTwo, A_TEST_REF_NAME, Type.UPDATE_NONFASTFORWARD);

    ReceiveCommand secondNormalUpdateWouldFailIfNoRollBack =
        new ReceiveCommand(commitObjectIdOne, commitObjectIdThree, A_TEST_REF_NAME, Type.UPDATE);

    InMemoryRepository repository = testRepo.getRepository();
    try (RevWalk rw = new RevWalk(repository)) {
      MultiSiteBatchRefUpdate batchOne = newBatchRefUpdate(repository);
      batchOne.addCommand(firstCommand);
      batchOne.execute(rw, NullProgressMonitor.INSTANCE);
      assertThat(firstCommand.getResult()).isEqualTo(Result.OK);

      MultiSiteBatchRefUpdate batchTwo = newBatchRefUpdate(repository);
      batchTwo.addCommand(aNonFastForwardUpdate);
      batchTwo.execute(rw, NullProgressMonitor.INSTANCE);
      assertThat(aNonFastForwardUpdate.getResult()).isEqualTo(Result.REJECTED_NONFASTFORWARD);

      MultiSiteBatchRefUpdate batchThree = newBatchRefUpdate(repository);
      batchThree.addCommand(secondNormalUpdateWouldFailIfNoRollBack);
      batchThree.execute(rw, NullProgressMonitor.INSTANCE);
      assertThat(secondNormalUpdateWouldFailIfNoRollBack.getResult()).isEqualTo(Result.OK);
    }
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
      MultiSiteBatchRefUpdate batchRefUpdate = newBatchRefUpdate(repository);

      batchRefUpdate.addCommand(firstCommand);
      batchRefUpdate.addCommand(aNonFastForwardUpdate);
      batchRefUpdate.addCommand(aNewCreate);

      batchRefUpdate.execute(rw, NullProgressMonitor.INSTANCE);

      assertThat(firstCommand.getResult()).isEqualTo(Result.REJECTED_OTHER_REASON);
      assertThat(aNonFastForwardUpdate.getResult()).isEqualTo(Result.REJECTED_NONFASTFORWARD);
      assertThat(aNewCreate.getResult()).isEqualTo(Result.REJECTED_OTHER_REASON);
      assertThat(
              batchRefUpdate.getCommands().stream()
                  .filter(cmd -> cmd.getResult() == Result.OK)
                  .count())
          .isEqualTo(0);
    }
    assertThat(existsDataInZkForCommand(firstCommand)).isEqualTo(false);
    assertThat(existsDataInZkForCommand(aNonFastForwardUpdate)).isEqualTo(false);
    assertThat(existsDataInZkForCommand(aNewCreate)).isEqualTo(false);
  }

  private boolean existsDataInZkForCommand(ReceiveCommand firstCommand) throws Exception {
    return zkSharedRefDatabase.exists(A_TEST_PROJECT_NAME, firstCommand.getRefName());
  }

  private MultiSiteBatchRefUpdate newBatchRefUpdate(Repository localGitRepo) {
    MultiSiteBatchRefUpdate multiSiteBatchRefUpdate =
        new MultiSiteBatchRefUpdate(
            zkSharedRefDatabase, A_TEST_PROJECT_NAME, localGitRepo.getRefDatabase());
    multiSiteBatchRefUpdate.setAllowNonFastForwards(false);
    return multiSiteBatchRefUpdate;
  }

  @Override
  public String testBranch() {
    return "branch_" + nameRule.getMethodName();
  }
}
