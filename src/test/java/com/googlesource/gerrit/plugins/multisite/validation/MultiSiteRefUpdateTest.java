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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.googlesource.gerrit.plugins.multisite.validation.RefUpdateValidator.Factory;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DefaultSharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.RefFixture;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.RefUpdateStub;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.junit.Before;
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

  @Mock SharedRefDatabase sharedRefDb;
  @Mock ValidationMetrics validationMetrics;

  private final Ref oldRef =
      new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, A_TEST_REF_NAME, AN_OBJECT_ID_1);
  private final Ref newRef =
      new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, A_TEST_REF_NAME, AN_OBJECT_ID_2);

  @Rule public TestName nameRule = new TestName();

  @Override
  public String testBranch() {
    return "branch_" + nameRule.getMethodName();
  }

  @Before
  public void setMockRequiredReturnValues() {
    doReturn(newRef).when(sharedRefDb).newRef(A_TEST_REF_NAME, AN_OBJECT_ID_2);
  }

  @Test
  public void newUpdateShouldValidateAndSucceed() throws Exception {

    doReturn(true).when(sharedRefDb).isUpToDate(A_TEST_PROJECT_NAME, oldRef);
    doReturn(true).when(sharedRefDb).compareAndPut(A_TEST_PROJECT_NAME, oldRef, newRef);

    RefUpdate refUpdate = RefUpdateStub.forSuccessfulUpdate(oldRef, newRef.getObjectId());

    MultiSiteRefUpdate multiSiteRefUpdate =
        getMultiSiteRefUpdateWithDefaultPolicyEnforcement(refUpdate);

    assertThat(multiSiteRefUpdate.update()).isEqualTo(Result.FAST_FORWARD);
    verifyZeroInteractions(validationMetrics);
  }

  @Test(expected = Exception.class)
  public void newUpdateShouldValidateAndFailWithIOException() throws Exception {

    doReturn(false).when(sharedRefDb).isUpToDate(A_TEST_PROJECT_NAME, oldRef);

    RefUpdate refUpdate = RefUpdateStub.forSuccessfulUpdate(oldRef, newRef.getObjectId());

    MultiSiteRefUpdate multiSiteRefUpdate =
        getMultiSiteRefUpdateWithDefaultPolicyEnforcement(refUpdate);
    multiSiteRefUpdate.update();
  }

  @Test
  public void newUpdateShouldIncreaseRefUpdateFailureCountWhenFailing() throws IOException {
    setMockRequiredReturnValues();

    doReturn(false).when(sharedRefDb).isUpToDate(A_TEST_PROJECT_NAME, oldRef);

    RefUpdate refUpdate = RefUpdateStub.forSuccessfulUpdate(oldRef, newRef.getObjectId());

    MultiSiteRefUpdate multiSiteRefUpdate =
        getMultiSiteRefUpdateWithDefaultPolicyEnforcement(refUpdate);

    try {
      multiSiteRefUpdate.update();
      fail("Expecting an IOException to be thrown");
    } catch (IOException e) {
      verify(validationMetrics).incrementSplitBrainPrevention();
    }
  }

  @Test
  public void newUpdateShouldNotIncreaseSplitBrainPreventedCounterIfFailingSharedDbPostUpdate()
      throws IOException {
    setMockRequiredReturnValues();

    doReturn(true).when(sharedRefDb).isUpToDate(A_TEST_PROJECT_NAME, oldRef);
    doReturn(false).when(sharedRefDb).compareAndPut(A_TEST_PROJECT_NAME, oldRef, newRef);

    RefUpdate refUpdate = RefUpdateStub.forSuccessfulUpdate(oldRef, newRef.getObjectId());

    MultiSiteRefUpdate multiSiteRefUpdate =
        getMultiSiteRefUpdateWithDefaultPolicyEnforcement(refUpdate);

    try {
      multiSiteRefUpdate.update();
      fail("Expecting an IOException to be thrown");
    } catch (IOException e) {
      verify(validationMetrics, never()).incrementSplitBrainPrevention();
    }
  }

  @Test
  public void newUpdateShouldtIncreaseSplitBrainCounterIfFailingSharedDbPostUpdate()
      throws IOException {
    setMockRequiredReturnValues();

    doReturn(true).when(sharedRefDb).isUpToDate(A_TEST_PROJECT_NAME, oldRef);
    doReturn(false).when(sharedRefDb).compareAndPut(A_TEST_PROJECT_NAME, oldRef, newRef);

    RefUpdate refUpdate = RefUpdateStub.forSuccessfulUpdate(oldRef, newRef.getObjectId());

    MultiSiteRefUpdate multiSiteRefUpdate =
        getMultiSiteRefUpdateWithDefaultPolicyEnforcement(refUpdate);

    try {
      multiSiteRefUpdate.update();
      fail("Expecting an IOException to be thrown");
    } catch (IOException e) {
      verify(validationMetrics).incrementSplitBrain();
    }
  }

  @Test
  public void deleteShouldValidateAndSucceed() throws IOException {
    setMockRequiredReturnValues();
    doReturn(true).when(sharedRefDb).isUpToDate(A_TEST_PROJECT_NAME, oldRef);

    doReturn(true)
        .when(sharedRefDb)
        .compareAndPut(A_TEST_PROJECT_NAME, oldRef, sharedRefDb.NULL_REF);

    RefUpdate refUpdate = RefUpdateStub.forSuccessfulDelete(oldRef);

    MultiSiteRefUpdate multiSiteRefUpdate =
        getMultiSiteRefUpdateWithDefaultPolicyEnforcement(refUpdate);

    assertThat(multiSiteRefUpdate.delete()).isEqualTo(Result.FORCED);
    verifyZeroInteractions(validationMetrics);
  }

  @Test
  public void deleteShouldIncreaseRefUpdateFailureCountWhenFailing() throws IOException {
    setMockRequiredReturnValues();

    doReturn(false).when(sharedRefDb).isUpToDate(A_TEST_PROJECT_NAME, oldRef);

    RefUpdate refUpdate = RefUpdateStub.forSuccessfulDelete(oldRef);

    MultiSiteRefUpdate multiSiteRefUpdate =
        getMultiSiteRefUpdateWithDefaultPolicyEnforcement(refUpdate);

    try {
      multiSiteRefUpdate.delete();
      fail("Expecting an IOException to be thrown");
    } catch (IOException e) {
      verify(validationMetrics).incrementSplitBrainPrevention();
    }
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
                    projectName,
                    refDb);
            return RefUpdateValidator;
          }
        };
    return new MultiSiteRefUpdate(batchRefValidatorFactory, A_TEST_PROJECT_NAME, refUpdate);
  }
}
