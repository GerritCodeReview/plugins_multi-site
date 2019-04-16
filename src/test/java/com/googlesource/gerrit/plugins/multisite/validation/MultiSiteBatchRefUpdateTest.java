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

import static org.mockito.Mockito.doReturn;

import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.RefFixture;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

@RunWith(MockitoJUnitRunner.class)
public class MultiSiteBatchRefUpdateTest implements RefFixture {

  @Mock SharedRefDatabase sharedRefDb;
  @Mock BatchRefUpdate batchRefUpdate;
  @Mock RefDatabase refDatabase;
  @Mock RevWalk revWalk;
  @Mock ProgressMonitor progressMonitor;
  @Mock ValidationMetrics validationMetrics;

  private final Ref oldRef =
      new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, A_TEST_REF_NAME, AN_OBJECT_ID_1);
  private final Ref newRef =
      new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, A_TEST_REF_NAME, AN_OBJECT_ID_2);
  ReceiveCommand receiveCommand =
      new ReceiveCommand(oldRef.getObjectId(), newRef.getObjectId(), oldRef.getName());

  private MultiSiteBatchRefUpdate multiSiteRefUpdate;

  @Rule public TestName nameRule = new TestName();

  @Override
  public String testBranch() {
    return "branch_" + nameRule.getMethodName();
  }

  private void setMockRequiredReturnValues() throws IOException {
    doReturn(batchRefUpdate).when(refDatabase).newBatchUpdate();
    doReturn(Arrays.asList(receiveCommand)).when(batchRefUpdate).getCommands();
    doReturn(oldRef).when(refDatabase).getRef(A_TEST_REF_NAME);
    doReturn(newRef).when(sharedRefDb).newRef(A_TEST_REF_NAME, AN_OBJECT_ID_2);

    multiSiteRefUpdate = new MultiSiteBatchRefUpdate(sharedRefDb, validationMetrics, A_TEST_PROJECT_NAME, refDatabase);

    verifyZeroInteractions(validationMetrics);
  }

  @Test
  public void executeAndDelegateSuccessfullyWithNoExceptions() throws IOException {
    setMockRequiredReturnValues();

    // When compareAndPut against sharedDb succeeds
    doReturn(true).when(sharedRefDb).compareAndPut(A_TEST_PROJECT_NAME, oldRef, newRef);
    multiSiteRefUpdate.execute(revWalk, progressMonitor, Collections.emptyList());
  }

  @Test
  public void executeAndFailsWithExceptions() throws IOException {
    setMockRequiredReturnValues();

    // When compareAndPut against sharedDb fails
    doReturn(false).when(sharedRefDb).compareAndPut(A_TEST_PROJECT_NAME, oldRef, newRef);
    try {
      multiSiteRefUpdate.execute(revWalk, progressMonitor, Collections.emptyList());
      fail("Expecting an IOException to be thrown");
    } catch (IOException e) {
      verify(validationMetrics).incrementSplitBrainRefUpdates();
    }
  }

  @Test
  public void executeSuccessfullyWithNoExceptionsWhenEmptyList() throws IOException {
    doReturn(batchRefUpdate).when(refDatabase).newBatchUpdate();
    doReturn(Collections.emptyList()).when(batchRefUpdate).getCommands();

    multiSiteRefUpdate = new MultiSiteBatchRefUpdate(sharedRefDb, validationMetrics, A_TEST_PROJECT_NAME, refDatabase);

    multiSiteRefUpdate.execute(revWalk, progressMonitor, Collections.emptyList());
  }
}
