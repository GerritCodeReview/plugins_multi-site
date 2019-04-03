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
import static org.mockito.Mockito.doReturn;

import com.googlesource.gerrit.plugins.multisite.validation.MultiSiteBatchRefUpdate.RefPair;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.RefFixture;
import java.io.IOException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MultiSiteBatchRefUpdateTest implements RefFixture {

  @Mock SharedRefDatabase sharedRefDb;
  @Mock RefUpdate refUpdate;
  @Mock RefDatabase refDb;

  @Rule public TestName nameRule = new TestName();

  @Override
  public String testBranch() {
    return "branch_" + nameRule.getMethodName();
  }

  @Test
  public void nonFirstPatchsetShouldReturnOldRefForPreviousPatchset() throws IOException {

    MultiSiteBatchRefUpdate msBatchRefUpdate =
        new MultiSiteBatchRefUpdate(sharedRefDb, A_TEST_PROJECT_NAME, refDb);

    Ref oldRefPatchset = newRef("refs/changes/01/1/1", AN_OBJECT_ID_1);
    Ref newRefPatchset = newRef("refs/changes/01/1/2", AN_OBJECT_ID_2);

    Ref expectedOldRefPatchset = newRef("refs/changes/01/changeObjectId", AN_OBJECT_ID_1);
    Ref expectedNewRefPatchset = newRef("refs/changes/01/changeObjectId", AN_OBJECT_ID_2);

    doReturn(oldRefPatchset).when(refDb).getRef("refs/changes/01/1/1");
    doReturn(expectedOldRefPatchset).when(sharedRefDb).cleansedChangeRefFor(oldRefPatchset);
    doReturn(expectedNewRefPatchset).when(sharedRefDb).cleansedChangeRefFor(newRefPatchset);

    RefPair expectedRefPair = new RefPair(expectedOldRefPatchset, expectedNewRefPatchset);
    RefPair resultRefPair = msBatchRefUpdate.getRefPairForNewRef(newRefPatchset);

    assertThat(resultRefPair.oldRef.getName()).isEqualTo(expectedRefPair.oldRef.getName());
    assertThat(resultRefPair.oldRef.getObjectId()).isEqualTo(expectedRefPair.oldRef.getObjectId());

    assertThat(resultRefPair.newRef.getName()).isEqualTo(expectedRefPair.newRef.getName());
    assertThat(resultRefPair.newRef.getObjectId()).isEqualTo(expectedRefPair.newRef.getObjectId());
  }

  @Test
  public void veryFirstPatchsetShouldReturnNullOldRef() throws IOException {

    MultiSiteBatchRefUpdate msBatchRefUpdate =
        new MultiSiteBatchRefUpdate(sharedRefDb, A_TEST_PROJECT_NAME, refDb);
    Ref expectedOldRefPatchset = sharedRefDb.NULL_REF;

    Ref newRefPatchset = newRef("refs/changes/01/1/1", AN_OBJECT_ID_1);
    Ref expectedNewRefPatchset = newRef("refs/changes/01/changeObjectId", AN_OBJECT_ID_1);

    doReturn(expectedNewRefPatchset).when(sharedRefDb).cleansedChangeRefFor(newRefPatchset);

    RefPair expectedRefPair = new RefPair(expectedOldRefPatchset, expectedNewRefPatchset);
    RefPair resultRefPair = msBatchRefUpdate.getRefPairForNewRef(newRefPatchset);

    assertThat(resultRefPair.oldRef.getName()).isEqualTo(expectedRefPair.oldRef.getName());
    assertThat(resultRefPair.oldRef.getObjectId()).isEqualTo(expectedRefPair.oldRef.getObjectId());

    assertThat(resultRefPair.newRef.getName()).isEqualTo(expectedRefPair.newRef.getName());
    assertThat(resultRefPair.newRef.getObjectId()).isEqualTo(expectedRefPair.newRef.getObjectId());
  }

  @Test
  public void nonChangeShouldReturnNullOldRef() throws IOException {

    MultiSiteBatchRefUpdate msBatchRefUpdate =
        new MultiSiteBatchRefUpdate(sharedRefDb, A_TEST_PROJECT_NAME, refDb);
    Ref expectedOldRefPatchset = sharedRefDb.NULL_REF;

    Ref newRefPatchset = newRef("refs/heads/master", AN_OBJECT_ID_1);
    Ref expectedNewRefPatchset = newRef("refs/heads/master", AN_OBJECT_ID_1);

    doReturn(expectedNewRefPatchset).when(sharedRefDb).cleansedChangeRefFor(newRefPatchset);

    RefPair expectedRefPair = new RefPair(expectedOldRefPatchset, expectedNewRefPatchset);
    RefPair resultRefPair = msBatchRefUpdate.getRefPairForNewRef(newRefPatchset);

    assertThat(resultRefPair.oldRef.getName()).isEqualTo(expectedRefPair.oldRef.getName());
    assertThat(resultRefPair.oldRef.getObjectId()).isEqualTo(expectedRefPair.oldRef.getObjectId());

    assertThat(resultRefPair.newRef.getName()).isEqualTo(expectedRefPair.newRef.getName());
    assertThat(resultRefPair.newRef.getObjectId()).isEqualTo(expectedRefPair.newRef.getObjectId());
  }
}
