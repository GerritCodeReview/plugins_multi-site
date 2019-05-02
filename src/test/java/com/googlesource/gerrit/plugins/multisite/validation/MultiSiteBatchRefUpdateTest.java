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

import static java.util.Arrays.asList;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.googlesource.gerrit.plugins.multisite.validation.RefUpdateValidator.Factory;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DefaultSharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.RefFixture;
import java.io.IOException;
import java.util.Collections;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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
  ReceiveCommand receiveCommandBeforeExecution =
      createReceiveCommand(
          oldRef.getObjectId(), newRef.getObjectId(), oldRef.getName(), Result.NOT_ATTEMPTED);

  ReceiveCommand successReceiveCommandAfterExecution =
      createReceiveCommand(oldRef.getObjectId(), newRef.getObjectId(), oldRef.getName(), Result.OK);

  ReceiveCommand rejectReceiveCommandAfterExecution =
      createReceiveCommand(
          oldRef.getObjectId(),
          newRef.getObjectId(),
          oldRef.getName(),
          Result.REJECTED_NONFASTFORWARD);

  private ReceiveCommand createReceiveCommand(
      ObjectId oldRefObjectId, ObjectId newRefObjectId, String refName, Result result) {
    ReceiveCommand receiveCommand = new ReceiveCommand(oldRefObjectId, newRefObjectId, refName);
    receiveCommand.setResult(result);
    return receiveCommand;
  }

  private MultiSiteBatchRefUpdate multiSiteRefUpdate;

  @Rule public TestName nameRule = new TestName();

  @Override
  public String testBranch() {
    return "branch_" + nameRule.getMethodName();
  }

  private void setMockRequiredReturnValues() throws IOException {

    doReturn(batchRefUpdate).when(refDatabase).newBatchUpdate();

    when(batchRefUpdate.getCommands())
        .thenReturn(asList(receiveCommandBeforeExecution))
        .thenReturn(asList(successReceiveCommandAfterExecution));

    doReturn(oldRef).when(refDatabase).getRef(A_TEST_REF_NAME);
    doCallRealMethod().when(sharedRefDb).newRef(anyString(), any(ObjectId.class));

    multiSiteRefUpdate = getMultiSiteBatchRefUpdateWithDefaultPolicyEnforcement();

    verifyZeroInteractions(validationMetrics);
  }

  @Test
  public void executeAndDelegateSuccessfullyWithNoExceptions() throws Exception {
    setMockRequiredReturnValues();

    // When compareAndPut against sharedDb succeeds
    doReturn(true).when(sharedRefDb).isMostRecentRefVersion(A_TEST_PROJECT_NAME, oldRef);
    doReturn(true)
        .when(sharedRefDb)
        .compareAndPut(eq(A_TEST_PROJECT_NAME), refEquals(oldRef), refEquals(newRef));
    multiSiteRefUpdate.execute(revWalk, progressMonitor, Collections.emptyList());
    verify(sharedRefDb)
        .compareAndPut(eq(A_TEST_PROJECT_NAME), refEquals(oldRef), refEquals(newRef));
  }

  private Ref refEquals(Ref oldRef) {
    return argThat(new RefMatcher(oldRef));
  }

  @Test
  public void executeAndFailsWithExceptions() throws IOException {
    setMockRequiredReturnValues();

    doReturn(false).when(sharedRefDb).isMostRecentRefVersion(A_TEST_PROJECT_NAME, oldRef);
    try {
      multiSiteRefUpdate.execute(revWalk, progressMonitor, Collections.emptyList());
      fail("Expecting an IOException to be thrown");
    } catch (IOException e) {
      verify(validationMetrics).incrementSplitBrainPrevention();
    }
  }

  @Test
  public void executeSuccessfullyWithNoExceptionsWhenEmptyList() throws IOException {
    doReturn(batchRefUpdate).when(refDatabase).newBatchUpdate();
    doReturn(Collections.emptyList()).when(batchRefUpdate).getCommands();

    multiSiteRefUpdate = getMultiSiteBatchRefUpdateWithDefaultPolicyEnforcement();

    multiSiteRefUpdate.execute(revWalk, progressMonitor, Collections.emptyList());
  }

  private MultiSiteBatchRefUpdate getMultiSiteBatchRefUpdateWithDefaultPolicyEnforcement() {
    Factory batchRefValidatorFactory =
        new Factory() {
          @Override
          public RefUpdateValidator create(String projectName, RefDatabase refDb) {
            RefUpdateValidator RefUpdateValidator =
                new RefUpdateValidator(
                    sharedRefDb,
                    validationMetrics,
                    new DefaultSharedRefEnforcement(),
                    projectName,
                    refDb);
            return RefUpdateValidator;
          }
        };
    return new MultiSiteBatchRefUpdate(batchRefValidatorFactory, A_TEST_PROJECT_NAME, refDatabase);
  }

  protected static class RefMatcher implements ArgumentMatcher<Ref> {
    private Ref left;

    public RefMatcher(Ref ref) {
      this.left = ref;
    }

    @Override
    public boolean matches(Ref right) {
      return left.getName().equals(right.getName())
          && left.getObjectId().equals(right.getObjectId());
    }
  }
}
