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

import com.googlesource.gerrit.plugins.multisite.validation.ZkConnectionConfig;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DefaultSharedRefEnforcement;
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
    int SLEEP_BETWEEN_RETRIES_MS = 30;
    long TRANSACTION_LOCK_TIMEOUT = 1000l;
    int NUMBER_OF_RETRIES = 5;

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

  @Override
  public String testBranch() {
    return "branch_" + nameRule.getMethodName();
  }
}
