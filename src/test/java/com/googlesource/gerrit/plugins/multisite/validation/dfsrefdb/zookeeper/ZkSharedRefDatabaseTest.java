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
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import org.apache.curator.retry.RetryNTimes;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
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

public class ZkSharedRefDatabaseTest extends AbstractDaemonTest implements RefFixture {
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
  public void shouldCompareAndCreateSuccessfully() throws Exception {
    Ref ref = refOf(AN_OBJECT_ID_1);

    assertThat(zkSharedRefDatabase.compareAndCreate(A_TEST_PROJECT_NAME, ref)).isTrue();

    assertThat(zookeeperContainer.readRefValueFromZk(A_TEST_PROJECT_NAME, ref))
        .isEqualTo(ref.getObjectId());
  }

  @Test
  public void shouldCompareAndPutSuccessfully() throws Exception {
    Ref oldRef = refOf(AN_OBJECT_ID_1);
    Ref newRef = refOf(AN_OBJECT_ID_2);
    String projectName = A_TEST_PROJECT_NAME;

    zookeeperContainer.createRefInZk(projectName, oldRef);

    assertThat(zkSharedRefDatabase.compareAndPut(projectName, oldRef, newRef)).isTrue();
  }

  @Test
  public void shouldFetchLatestObjectIdInZk() throws Exception {
    Ref oldRef = refOf(AN_OBJECT_ID_1);
    Ref newRef = refOf(AN_OBJECT_ID_2);
    String projectName = A_TEST_PROJECT_NAME;

    zookeeperContainer.createRefInZk(projectName, oldRef);

    assertThat(zkSharedRefDatabase.compareAndPut(projectName, oldRef, newRef)).isTrue();

    assertThat(zkSharedRefDatabase.isMostRecentRefVersion(projectName, newRef)).isTrue();
    assertThat(zkSharedRefDatabase.isMostRecentRefVersion(projectName, oldRef)).isFalse();
  }

  @Test
  public void shouldCompareAndPutWithNullOldRefSuccessfully() throws Exception {
    Ref oldRef = refOf(null);
    Ref newRef = refOf(AN_OBJECT_ID_2);
    String projectName = A_TEST_PROJECT_NAME;

    zookeeperContainer.createRefInZk(projectName, oldRef);

    assertThat(zkSharedRefDatabase.compareAndPut(projectName, oldRef, newRef)).isTrue();
  }

  @Test
  public void compareAndPutShouldFailIfTheObjectionHasNotTheExpectedValue() throws Exception {
    String projectName = A_TEST_PROJECT_NAME;

    Ref oldRef = refOf(AN_OBJECT_ID_1);
    Ref expectedRef = refOf(AN_OBJECT_ID_2);

    zookeeperContainer.createRefInZk(projectName, oldRef);

    assertThat(zkSharedRefDatabase.compareAndPut(projectName, expectedRef, refOf(AN_OBJECT_ID_3)))
        .isFalse();
  }

  @Test
  public void shouldCompareAndRemoveSuccessfully() throws Exception {
    Ref oldRef = refOf(AN_OBJECT_ID_1);
    String projectName = A_TEST_PROJECT_NAME;

    zookeeperContainer.createRefInZk(projectName, oldRef);

    assertThat(zkSharedRefDatabase.compareAndRemove(projectName, oldRef)).isTrue();
  }

  @Test
  public void shouldReplaceTheRefWithATombstoneAfterCompareAndPutRemove() throws Exception {
    Ref oldRef = refOf(AN_OBJECT_ID_1);

    zookeeperContainer.createRefInZk(A_TEST_PROJECT_NAME, oldRef);

    assertThat(zkSharedRefDatabase.compareAndRemove(A_TEST_PROJECT_NAME, oldRef)).isTrue();

    assertThat(zookeeperContainer.readRefValueFromZk(A_TEST_PROJECT_NAME, oldRef))
        .isEqualTo(ObjectId.zeroId());
  }

  @Test
  public void shouldNotCompareAndPutSuccessfullyAfterACompareAndRemove() throws Exception {
    Ref oldRef = refOf(AN_OBJECT_ID_1);
    String projectName = A_TEST_PROJECT_NAME;

    zookeeperContainer.createRefInZk(projectName, oldRef);

    zkSharedRefDatabase.compareAndRemove(projectName, oldRef);
    assertThat(zkSharedRefDatabase.compareAndPut(projectName, oldRef, refOf(AN_OBJECT_ID_2)))
        .isFalse();
  }

  private Ref refOf(ObjectId objectId) {
    return zkSharedRefDatabase.newRef(aBranchRef(), objectId);
  }

  @Test
  public void immutableChangeShouldReturnTrue() throws Exception {
    Ref changeRef = zkSharedRefDatabase.newRef("refs/changes/01/1/1", AN_OBJECT_ID_1);

    boolean shouldReturnTrue =
        zkSharedRefDatabase.compareAndPut(
            A_TEST_PROJECT_NAME, SharedRefDatabase.NULL_REF, changeRef);

    assertThat(shouldReturnTrue).isTrue();
  }

  @Test(expected = Exception.class)
  public void immutableChangeShouldNotBeStored() throws Exception {
    Ref changeRef = zkSharedRefDatabase.newRef(A_REF_NAME_OF_A_PATCHSET, AN_OBJECT_ID_1);
    zkSharedRefDatabase.compareAndPut(A_TEST_PROJECT_NAME, SharedRefDatabase.NULL_REF, changeRef);
    zookeeperContainer.readRefValueFromZk(A_TEST_PROJECT_NAME, changeRef);
  }

  @Test
  public void anImmutableChangeShouldBeIgnored() {
    Ref immutableChangeRef = zkSharedRefDatabase.newRef(A_REF_NAME_OF_A_PATCHSET, AN_OBJECT_ID_1);
    assertThat(zkSharedRefDatabase.ignoreRefInSharedDb(immutableChangeRef.getName())).isTrue();
  }

  @Test
  public void aChangeMetaShouldNotBeIgnored() {
    Ref immutableChangeRef = zkSharedRefDatabase.newRef("refs/changes/01/1/meta", AN_OBJECT_ID_1);
    assertThat(zkSharedRefDatabase.ignoreRefInSharedDb(immutableChangeRef.getName())).isFalse();
  }

  @Test
  public void aDraftCommentsShouldBeIgnored() {
    Ref immutableChangeRef =
        zkSharedRefDatabase.newRef("refs/draft-comments/01/1/1000000", AN_OBJECT_ID_1);
    assertThat(zkSharedRefDatabase.ignoreRefInSharedDb(immutableChangeRef.getName())).isTrue();
  }

  @Test
  public void regularRefHeadsMasterShouldNotBeIgnored() {
    Ref immutableChangeRef = zkSharedRefDatabase.newRef("refs/heads/master", AN_OBJECT_ID_1);
    assertThat(zkSharedRefDatabase.ignoreRefInSharedDb(immutableChangeRef.getName())).isFalse();
  }

  @Test
  public void regularCommitShouldNotBeIgnored() {
    Ref immutableChangeRef = zkSharedRefDatabase.newRef("refs/heads/stable-2.16", AN_OBJECT_ID_1);
    assertThat(zkSharedRefDatabase.ignoreRefInSharedDb(immutableChangeRef.getName())).isFalse();
  }

  @Test
  public void compareAndPutShouldAlwaysIngoreAlwaysDraftCommentsEvenOutOfOrder() throws Exception {
    // Test to reproduce a production bug where ignored refs were persisted in ZK because
    // newRef == NULL
    Ref existingRef =
        zkSharedRefDatabase.newRef("refs/draft-comments/56/450756/1013728", AN_OBJECT_ID_1);
    Ref oldRefToIgnore =
        zkSharedRefDatabase.newRef("refs/draft-comments/56/450756/1013728", AN_OBJECT_ID_2);
    Ref nullRef = SharedRefDatabase.NULL_REF;
    String projectName = A_TEST_PROJECT_NAME;

    // This ref should be ignored even if newRef is null
    assertThat(zkSharedRefDatabase.compareAndPut(A_TEST_PROJECT_NAME, existingRef, nullRef))
        .isTrue();

    // This ignored ref should also be ignored
    assertThat(zkSharedRefDatabase.compareAndPut(projectName, oldRefToIgnore, nullRef)).isTrue();
  }

  @Test
  public void sequenceOfGitUpdatesWithARejectionCausesZKCheckToFail() throws Exception {
    ObjectId commitObjectIdOne = commitBuilder().add("file1.txt", "A").create().getId();
    ObjectId commitObjectIdTwo = commitBuilder().add("file1.txt", "B").create().getId();
    ObjectId commitObjectIdThree = commitBuilder().add("file1.txt", "A2").create().getId();

    ReceiveCommand firstCommand =
        new ReceiveCommand(ObjectId.zeroId(), commitObjectIdOne, A_TEST_REF_NAME);

    ReceiveCommand aNonFastForwardUpdate =
        new ReceiveCommand(
            commitObjectIdOne, commitObjectIdTwo, A_TEST_REF_NAME, Type.UPDATE_NONFASTFORWARD);

    ReceiveCommand secondNormalUpdateWillFail =
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
      batchThree.addCommand(secondNormalUpdateWillFail);
      batchThree.execute(rw, NullProgressMonitor.INSTANCE);
      assertThat(secondNormalUpdateWillFail.getResult()).isEqualTo(Result.OK);
    }
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
