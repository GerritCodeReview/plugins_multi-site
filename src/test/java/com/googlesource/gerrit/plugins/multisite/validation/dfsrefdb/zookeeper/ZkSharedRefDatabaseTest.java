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

import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DefaultSharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement;
import org.apache.curator.retry.RetryNTimes;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class ZkSharedRefDatabaseTest implements RefFixture {
  @Rule public TestName nameRule = new TestName();

  ZookeeperTestContainerSupport zookeeperContainer;
  ZkSharedRefDatabase zkSharedRefDatabase;
  SharedRefEnforcement refEnforcement;

  @Before
  public void setup() {
    refEnforcement = new DefaultSharedRefEnforcement();
    zookeeperContainer = new ZookeeperTestContainerSupport(false);
    zkSharedRefDatabase =
        new ZkSharedRefDatabase(
            zookeeperContainer.getCurator(), new RetryNTimes(5, 30), refEnforcement);
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
  public void compareAndPutShouldSucceedIfTheObjectionHasNotTheExpectedValueWithDesiredEnforcement()
      throws Exception {
    String projectName = "All-Users";
    String externalIds = "refs/meta/external-ids";

    Ref oldRef = zkSharedRefDatabase.newRef(externalIds, AN_OBJECT_ID_1);
    Ref expectedRef = zkSharedRefDatabase.newRef(externalIds, AN_OBJECT_ID_2);

    zookeeperContainer.createRefInZk(projectName, oldRef);

    assertThat(zkSharedRefDatabase.compareAndPut(projectName, expectedRef, refOf(AN_OBJECT_ID_3)))
        .isTrue();
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

  @Override
  public String testBranch() {
    return "branch_" + nameRule.getMethodName();
  }
}
