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

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.SharedRefDatabaseWrapper;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DefaultSharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.RefFixture;
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MultiSiteBatchRefUpdateTest implements RefFixture {

  @Rule public InMemoryTestEnvironment testEnvironment = new InMemoryTestEnvironment();
  @Mock SharedRefDatabaseWrapper sharedRefDb;
  @Mock BatchRefUpdate batchRefUpdate;
  @Mock RefDatabase refDatabase;
  @Mock RevWalk revWalk;
  @Mock ProgressMonitor progressMonitor;
  @Mock ValidationMetrics validationMetrics;
  @Inject private InMemoryRepositoryManager repoManager;

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

  @Before
  public void setUp() throws Exception {
    repoManager.createRepository(A_TEST_PROJECT_NAME_KEY);
  }

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
    doReturn(oldRef).when(refDatabase).exactRef(A_TEST_REF_NAME);

    multiSiteRefUpdate = getMultiSiteBatchRefUpdateWithDefaultPolicyEnforcement();

    verifyZeroInteractions(validationMetrics);
  }

  @Test
  public void executeAndDelegateSuccessfullyWithNoExceptions() throws Exception {
    setMockRequiredReturnValues();

    // When compareAndPut against sharedDb succeeds
    doReturn(true).when(sharedRefDb).isUpToDate(A_TEST_PROJECT_NAME_KEY, oldRef);
    doReturn(true)
        .when(sharedRefDb)
        .compareAndPut(eq(A_TEST_PROJECT_NAME_KEY), refEquals(oldRef), eq(newRef.getObjectId()));
    multiSiteRefUpdate.execute(revWalk, progressMonitor, Collections.emptyList());
    verify(sharedRefDb)
        .compareAndPut(eq(A_TEST_PROJECT_NAME_KEY), refEquals(oldRef), eq(newRef.getObjectId()));
  }

  private Ref refEquals(Ref oldRef) {
    return argThat(new RefMatcher(oldRef));
  }

  @Test
  public void executeAndFailsWithExceptions() throws IOException {
    setMockRequiredReturnValues();
    doReturn(true).when(sharedRefDb).exists(A_TEST_PROJECT_NAME_KEY, A_TEST_REF_NAME);
    doReturn(false).when(sharedRefDb).isUpToDate(A_TEST_PROJECT_NAME_KEY, oldRef);
    try {
      multiSiteRefUpdate.execute(revWalk, progressMonitor, Collections.emptyList());
      fail("Expecting an IOException to be thrown");
    } catch (IOException e) {
      verify(validationMetrics).incrementSplitBrainPrevention();
    }
  }

  @Test
  public void executeAndFailsWithExceptionsWhenCannotCreateVersionCommand() throws IOException {

    multiSiteRefUpdate =
        getMultiSiteBatchRefUpdateWithDefaultPolicyEnforcement("nonExistentProject");

    try {
      multiSiteRefUpdate.execute(revWalk, progressMonitor, Collections.emptyList());
      fail("Expecting an IOException to be thrown");
    } catch (IOException e) {
      // XXX Better creating an exception for the missing creation of the versioning command
      assertThat(e.getMessage()).contains("Cannot create versioning command");
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
    return getMultiSiteBatchRefUpdateWithDefaultPolicyEnforcement(A_TEST_PROJECT_NAME);
  }

  private MultiSiteBatchRefUpdate getMultiSiteBatchRefUpdateWithDefaultPolicyEnforcement(
      String projectName) {
    BatchRefUpdateValidator.Factory batchRefValidatorFactory =
        new BatchRefUpdateValidator.Factory() {
          @Override
          public BatchRefUpdateValidator create(String projectName, RefDatabase refDb) {
            return new BatchRefUpdateValidator(
                sharedRefDb,
                validationMetrics,
                new DefaultSharedRefEnforcement(),
                new DummyLockWrapper(),
                projectName,
                refDb);
          }
        };
    return new MultiSiteBatchRefUpdate(
        batchRefValidatorFactory, repoManager, projectName, refDatabase);
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
