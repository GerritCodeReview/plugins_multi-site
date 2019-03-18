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

import java.io.IOException;
import org.apache.curator.retry.RetryNTimes;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Ref.Storage;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class ZkSharedRefDatabaseTest implements RefFixture {
  @Rule public TestName nameRule = new TestName();

  ZookeeperTestContainerSupport zookeeperContainer;
  ZkSharedRefDatabase zkSharedRefDatabase;

  @Before
  public void setup() {
    zookeeperContainer = new ZookeeperTestContainerSupport(false);
    zkSharedRefDatabase =
        new ZkSharedRefDatabase(
            zookeeperContainer.getCurator(),
            new RetryNTimes(5, 30),
            ZkSharedRefDatabase.OperationMode.NORMAL);
  }

  @After
  public void cleanup() {
    zookeeperContainer.cleanup();
  }

  @Test
  public void shouldCreateANewRef() {

    ObjectId objectId = AN_OBJECT_ID_1;
    String refName = aBranchRef();

    Ref aNewRef = zkSharedRefDatabase.newRef(refName, objectId);

    assertThat(aNewRef.getName()).isEqualTo(refName);
    assertThat(aNewRef.getObjectId()).isEqualTo(objectId);
    assertThat(aNewRef.getStorage()).isEqualTo(Storage.NETWORK);
  }

  @Test
  public void shouldCompareAndCreateSuccessfully() throws Exception {
    Ref ref = refOf(AN_OBJECT_ID_1);

    zookeeperContainer.createRefInZk(A_TEST_PROJECT_NAME, ref);

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
  public void compareAndPutShouldFailIfTheObjectionHasNotTheExpectedValue() throws Exception {
    String projectName = A_TEST_PROJECT_NAME;

    Ref oldRef = refOf(AN_OBJECT_ID_1);
    Ref expectedRef = refOf(AN_OBJECT_ID_2);

    zookeeperContainer.createRefInZk(projectName, oldRef);

    assertThat(zkSharedRefDatabase.compareAndPut(projectName, expectedRef, refOf(AN_OBJECT_ID_3)))
        .isFalse();
  }

  @Test
  public void compareAndPutShouldFaiIfTheObjectionDoesNotExist() throws IOException {
    Ref oldRef = refOf(AN_OBJECT_ID_1);
    assertThat(
            zkSharedRefDatabase.compareAndPut(A_TEST_PROJECT_NAME, oldRef, refOf(AN_OBJECT_ID_2)))
        .isFalse();
  }

  @Test
  public void compareAndPutShouldDoAnInsertIfTheObjectionDoesNotExistAndInMigrationMode()
      throws Exception {
    zkSharedRefDatabase =
        new ZkSharedRefDatabase(
            zookeeperContainer.getCurator(),
            new RetryNTimes(5, 30),
            ZkSharedRefDatabase.OperationMode.MIGRATION);

    Ref oldRef = refOf(AN_OBJECT_ID_1);
    assertThat(
            zkSharedRefDatabase.compareAndPut(A_TEST_PROJECT_NAME, oldRef, refOf(AN_OBJECT_ID_2)))
        .isTrue();

    assertThat(zookeeperContainer.readRefValueFromZk(A_TEST_PROJECT_NAME, oldRef))
        .isEqualTo(AN_OBJECT_ID_2);
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

  @Override
  public String testBranch() {
    return "branch_" + nameRule.getMethodName();
  }
}
