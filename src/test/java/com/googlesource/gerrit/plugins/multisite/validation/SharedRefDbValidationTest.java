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

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import com.googlesource.gerrit.plugins.multisite.validation.MultiSiteBatchRefUpdateTest.RefMatcher;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.RefFixture;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SharedRefDbValidationTest implements RefFixture {

  @Mock SharedRefDatabase sharedRefDb;
  @Mock Repository localRepository;
  @Mock RefUpdate refUpdate;

  private Ref refOne =
      new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, "refs/heads/master", AN_OBJECT_ID_1);
  private Ref refTwo =
      new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, "refs/for/changes/01/1/meta", AN_OBJECT_ID_2);

  private RemoteRefUpdate remoteRefUpdateOne;
  private RemoteRefUpdate remoteRefUpdateTwo;

  private Collection<RemoteRefUpdate> refsToPush = new ArrayList<>();

  @Before
  public void initCommonMocks() throws IOException {
    doReturn(AN_OBJECT_ID_1).when(localRepository).resolve(refOne.getName());
    doReturn(AN_OBJECT_ID_2).when(localRepository).resolve(refTwo.getName());

    doNothing().when(refUpdate).setForceUpdate(true);
    doReturn(refUpdate).when(localRepository).updateRef("bar");

    remoteRefUpdateOne =
        new RemoteRefUpdate(
            localRepository, refOne.getName(), "foo", true, "bar", refOne.getObjectId());
    remoteRefUpdateTwo =
        new RemoteRefUpdate(
            localRepository, refTwo.getName(), "foo", true, "bar", refTwo.getObjectId());

    refsToPush.add(remoteRefUpdateOne);
    refsToPush.add(remoteRefUpdateTwo);
  }

  @Test
  public void remoteRefUpdatesShouldNotBeFilteredOutIfInSyncWithSharedRefDb() throws Exception {
    // Refs to push are the most up to date
    doReturn(true).when(sharedRefDb).isUpToDate(eq(A_TEST_PROJECT_NAME), refEquals(refOne));
    doReturn(true).when(sharedRefDb).isUpToDate(eq(A_TEST_PROJECT_NAME), refEquals(refTwo));

    assertThat(
        SharedRefDbValidation.filterOutOfSyncRemoteRefUpdates(
            A_TEST_PROJECT_NAME, sharedRefDb, refsToPush),
        is(refsToPush));
  }

  @Test
  public void remoteRefUpdatesShouldBeFilteredOutIfOutOfSyncWithSharedRefDb() throws Exception {
    // Refs to push are not the most up to date
    doReturn(false).when(sharedRefDb).isUpToDate(eq(A_TEST_PROJECT_NAME), refEquals(refOne));
    doReturn(false).when(sharedRefDb).isUpToDate(eq(A_TEST_PROJECT_NAME), refEquals(refTwo));

    assertTrue(
        SharedRefDbValidation.filterOutOfSyncRemoteRefUpdates(
                A_TEST_PROJECT_NAME, sharedRefDb, refsToPush)
            .isEmpty());
  }

  @Rule public TestName nameRule = new TestName();

  private Ref refEquals(Ref oldRef) {
    return argThat(new RefMatcher(oldRef));
  }

  @Override
  public String testBranch() {
    return null;
  }
}
