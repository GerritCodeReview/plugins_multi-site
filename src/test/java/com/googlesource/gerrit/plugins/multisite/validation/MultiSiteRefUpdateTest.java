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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.googlesource.gerrit.plugins.multisite.SharedRefDatabaseWrapper;
import com.googlesource.gerrit.plugins.multisite.validation.RefUpdateValidator.Factory;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DefaultSharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.RefFixture;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.RefUpdateStub;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@Ignore // The focus of this test suite is unclear and all tests are failing when the code is
// working, and the other way around
public class MultiSiteRefUpdateTest implements RefFixture {

  @Mock SharedRefDatabaseWrapper sharedRefDb;
  @Mock ValidationMetrics validationMetrics;
  @Mock RefDatabase refDb;

  private final Ref oldRef =
      new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, A_TEST_REF_NAME, AN_OBJECT_ID_1);
  private final Ref newRef =
      new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, A_TEST_REF_NAME, AN_OBJECT_ID_2);

  @Rule public TestName nameRule = new TestName();

  @Override
  public String testBranch() {
    return "branch_" + nameRule.getMethodName();
  }

  @Test
  public void newUpdateShouldValidateAndSucceed() throws Exception {

    doReturn(true).when(sharedRefDb).isUpToDate(A_TEST_PROJECT_NAME_KEY, oldRef);
    doReturn(true)
        .when(sharedRefDb)
        .compareAndPut(A_TEST_PROJECT_NAME_KEY, oldRef, newRef.getObjectId());

    RefUpdate refUpdate = RefUpdateStub.forSuccessfulUpdate(oldRef, newRef.getObjectId());

    MultiSiteRefUpdate multiSiteRefUpdate =
        getMultiSiteRefUpdateWithDefaultPolicyEnforcement(refUpdate);

    assertThat(multiSiteRefUpdate.update()).isEqualTo(Result.FAST_FORWARD);
    verifyZeroInteractions(validationMetrics);
  }

  @Test(expected = Exception.class)
  public void newUpdateShouldValidateAndFailWithIOException() throws Exception {

    doReturn(false).when(sharedRefDb).isUpToDate(A_TEST_PROJECT_NAME_KEY, oldRef);

    RefUpdate refUpdate = RefUpdateStub.forSuccessfulUpdate(oldRef, newRef.getObjectId());

    MultiSiteRefUpdate multiSiteRefUpdate =
        getMultiSiteRefUpdateWithDefaultPolicyEnforcement(refUpdate);
    multiSiteRefUpdate.update();
  }

  @Test
  public void newUpdateShouldIncreaseRefUpdateFailureCountWhenFailing() {

    doReturn(false).when(sharedRefDb).isUpToDate(A_TEST_PROJECT_NAME_KEY, oldRef);

    RefUpdate refUpdate = RefUpdateStub.forSuccessfulUpdate(oldRef, newRef.getObjectId());

    MultiSiteRefUpdate multiSiteRefUpdate =
        getMultiSiteRefUpdateWithDefaultPolicyEnforcement(refUpdate);

    assertThrows(IOException.class, () -> multiSiteRefUpdate.update());
    verify(validationMetrics).incrementSplitBrainPrevention();
  }

  @Test
  public void newUpdateShouldNotIncreaseSplitBrainPreventedCounterIfFailingSharedDbPostUpdate() {

    doReturn(true).when(sharedRefDb).isUpToDate(A_TEST_PROJECT_NAME_KEY, oldRef);
    doReturn(false)
        .when(sharedRefDb)
        .compareAndPut(A_TEST_PROJECT_NAME_KEY, oldRef, newRef.getObjectId());

    RefUpdate refUpdate = RefUpdateStub.forSuccessfulUpdate(oldRef, newRef.getObjectId());

    MultiSiteRefUpdate multiSiteRefUpdate =
        getMultiSiteRefUpdateWithDefaultPolicyEnforcement(refUpdate);

    assertThrows(IOException.class, () -> multiSiteRefUpdate.update());
    verify(validationMetrics, never()).incrementSplitBrainPrevention();
  }

  @Test
  public void newUpdateShouldtIncreaseSplitBrainCounterIfFailingSharedDbPostUpdate() {

    doReturn(true).when(sharedRefDb).isUpToDate(A_TEST_PROJECT_NAME_KEY, oldRef);
    doReturn(false)
        .when(sharedRefDb)
        .compareAndPut(A_TEST_PROJECT_NAME_KEY, oldRef, newRef.getObjectId());

    RefUpdate refUpdate = RefUpdateStub.forSuccessfulUpdate(oldRef, newRef.getObjectId());

    MultiSiteRefUpdate multiSiteRefUpdate =
        getMultiSiteRefUpdateWithDefaultPolicyEnforcement(refUpdate);

    assertThrows(IOException.class, () -> multiSiteRefUpdate.update());
    verify(validationMetrics).incrementSplitBrain();
  }

  @Test
  public void deleteShouldValidateAndSucceed() throws IOException {
    doReturn(true).when(sharedRefDb).isUpToDate(A_TEST_PROJECT_NAME_KEY, oldRef);

    doReturn(true)
        .when(sharedRefDb)
        .compareAndPut(A_TEST_PROJECT_NAME_KEY, oldRef, ObjectId.zeroId());

    RefUpdate refUpdate = RefUpdateStub.forSuccessfulDelete(oldRef);

    MultiSiteRefUpdate multiSiteRefUpdate =
        getMultiSiteRefUpdateWithDefaultPolicyEnforcement(refUpdate);

    assertThat(multiSiteRefUpdate.delete()).isEqualTo(Result.FORCED);
    verifyZeroInteractions(validationMetrics);
  }

  @Test
  public void deleteShouldIncreaseRefUpdateFailureCountWhenFailing() {

    doReturn(false).when(sharedRefDb).isUpToDate(A_TEST_PROJECT_NAME_KEY, oldRef);

    RefUpdate refUpdate = RefUpdateStub.forSuccessfulDelete(oldRef);

    MultiSiteRefUpdate multiSiteRefUpdate =
        getMultiSiteRefUpdateWithDefaultPolicyEnforcement(refUpdate);

    assertThrows(IOException.class, () -> multiSiteRefUpdate.delete());
    verify(validationMetrics).incrementSplitBrainPrevention();
  }

  private MultiSiteRefUpdate getMultiSiteRefUpdateWithDefaultPolicyEnforcement(
      RefUpdate refUpdate) {
    Factory batchRefValidatorFactory =
        new Factory() {
          @Override
          public RefUpdateValidator create(String projectName, RefDatabase refDb) {
            RefUpdateValidator RefUpdateValidator =
                new RefUpdateValidator(
                    sharedRefDb,
                    validationMetrics,
                    new DefaultSharedRefEnforcement(),
                    new DummyLockWrapper(),
                    projectName,
                    refDb);
            return RefUpdateValidator;
          }
        };
    return new MultiSiteRefUpdate(batchRefValidatorFactory, A_TEST_PROJECT_NAME, refUpdate, refDb);
  }
}
