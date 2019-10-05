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
import static org.mockito.Mockito.mock;

import com.gerritforge.gerrit.globalrefdb.GlobalRefDatabase;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Project;
import com.googlesource.gerrit.plugins.multisite.SharedRefDatabaseWrapper;
import com.googlesource.gerrit.plugins.multisite.validation.DisabledSharedRefLogger;
import com.googlesource.gerrit.plugins.multisite.validation.ProjectDeletedSharedDbCleanup;
import com.googlesource.gerrit.plugins.multisite.validation.ValidationMetrics;
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
  SharedRefDatabaseWrapper zkSharedRefDatabase;
  SharedRefEnforcement refEnforcement;

  ValidationMetrics mockValidationMetrics;

  @Before
  public void setup() {
    refEnforcement = new DefaultSharedRefEnforcement();
    zookeeperContainer = new ZookeeperTestContainerSupport(false);
    int SLEEP_BETWEEN_RETRIES_MS = 30;
    long TRANSACTION_LOCK_TIMEOUT = 1000l;
    int NUMBER_OF_RETRIES = 5;

    zkSharedRefDatabase =
        new SharedRefDatabaseWrapper(
            DynamicItem.itemOf(
                GlobalRefDatabase.class,
                new ZkSharedRefDatabase(
                    zookeeperContainer.getCurator(),
                    new ZkConnectionConfig(
                        new RetryNTimes(NUMBER_OF_RETRIES, SLEEP_BETWEEN_RETRIES_MS),
                        TRANSACTION_LOCK_TIMEOUT))),
            new DisabledSharedRefLogger());

    mockValidationMetrics = mock(ValidationMetrics.class);
  }

  @After
  public void cleanup() {
    zookeeperContainer.cleanup();
  }

  @Test
  public void shouldCompareAndPutSuccessfully() throws Exception {
    Ref oldRef = refOf(AN_OBJECT_ID_1);
    Ref newRef = refOf(AN_OBJECT_ID_2);
    Project.NameKey projectNameKey = A_TEST_PROJECT_NAME_KEY;

    zookeeperContainer.createRefInZk(projectNameKey, oldRef);

    assertThat(zkSharedRefDatabase.compareAndPut(projectNameKey, oldRef, newRef.getObjectId()))
        .isTrue();
  }

  @Test
  public void shouldFetchLatestObjectIdInZk() throws Exception {
    Ref oldRef = refOf(AN_OBJECT_ID_1);
    Ref newRef = refOf(AN_OBJECT_ID_2);
    Project.NameKey projectNameKey = A_TEST_PROJECT_NAME_KEY;

    zookeeperContainer.createRefInZk(projectNameKey, oldRef);

    assertThat(zkSharedRefDatabase.compareAndPut(projectNameKey, oldRef, newRef.getObjectId()))
        .isTrue();

    assertThat(zkSharedRefDatabase.isUpToDate(projectNameKey, newRef)).isTrue();
    assertThat(zkSharedRefDatabase.isUpToDate(projectNameKey, oldRef)).isFalse();
  }

  @Test
  public void shouldCompareAndPutWithNullOldRefSuccessfully() throws Exception {
    Ref oldRef = refOf(null);
    Ref newRef = refOf(AN_OBJECT_ID_2);
    Project.NameKey projectNameKey = A_TEST_PROJECT_NAME_KEY;

    zookeeperContainer.createRefInZk(projectNameKey, oldRef);

    assertThat(zkSharedRefDatabase.compareAndPut(projectNameKey, oldRef, newRef.getObjectId()))
        .isTrue();
  }

  @Test
  public void compareAndPutShouldFailIfTheObjectionHasNotTheExpectedValue() throws Exception {
    Project.NameKey projectNameKey = A_TEST_PROJECT_NAME_KEY;

    Ref oldRef = refOf(AN_OBJECT_ID_1);
    Ref expectedRef = refOf(AN_OBJECT_ID_2);

    zookeeperContainer.createRefInZk(projectNameKey, oldRef);

    assertThat(zkSharedRefDatabase.compareAndPut(projectNameKey, expectedRef, AN_OBJECT_ID_3))
        .isFalse();
  }

  @Test
  public void removeProjectShouldRemoveTheWholePathInZk() throws Exception {
    Project.NameKey projectNameKey = A_TEST_PROJECT_NAME_KEY;
    Ref someRef = refOf(AN_OBJECT_ID_1);

    zookeeperContainer.createRefInZk(projectNameKey, someRef);

    assertThat(zookeeperContainer.readRefValueFromZk(projectNameKey, someRef))
        .isEqualTo(AN_OBJECT_ID_1);

    assertThat(getNumChildrenForPath("/")).isEqualTo(1);

    zkSharedRefDatabase.remove(projectNameKey);

    assertThat(getNumChildrenForPath("/")).isEqualTo(0);
  }

  @Test
  public void aDeleteProjectEventShouldCleanupProjectFromZk() throws Exception {
    String projectName = A_TEST_PROJECT_NAME;
    Project.NameKey projectNameKey = A_TEST_PROJECT_NAME_KEY;
    Ref someRef = refOf(AN_OBJECT_ID_1);
    ProjectDeletedSharedDbCleanup projectDeletedSharedDbCleanup =
        new ProjectDeletedSharedDbCleanup(zkSharedRefDatabase, mockValidationMetrics);

    ProjectDeletedListener.Event event =
        new ProjectDeletedListener.Event() {
          @Override
          public String getProjectName() {
            return projectName;
          }

          @Override
          public NotifyHandling getNotify() {
            return NotifyHandling.NONE;
          }
        };

    zookeeperContainer.createRefInZk(projectNameKey, someRef);

    assertThat(getNumChildrenForPath("/")).isEqualTo(1);

    projectDeletedSharedDbCleanup.onProjectDeleted(event);

    assertThat(getNumChildrenForPath("/")).isEqualTo(0);
  }

  @Override
  public String testBranch() {
    return "branch_" + nameRule.getMethodName();
  }

  private int getNumChildrenForPath(String path) throws Exception {
    return zookeeperContainer
        .getCurator()
        .checkExists()
        .forPath(String.format(path))
        .getNumChildren();
  }

  private Ref refOf(ObjectId objectId) {
    return newRef(aBranchRef(), objectId);
  }
}
