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

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.RefFixture;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MultiSiteRefDatabaseTest implements RefFixture {

  @Rule public TestName nameRule = new TestName();

  @Mock MultiSiteRefUpdate.Factory refUpdateFactoryMock;
  @Mock MultiSiteBatchRefUpdate.Factory refBatchUpdateFactoryMock;

  @Mock RefDatabase refDatabaseMock;

  @Mock RefUpdate refUpdateMock;

  @Override
  public String testBranch() {
    return "branch_" + nameRule.getMethodName();
  }

  @Test
  public void newUpdateShouldCreateMultiSiteRefUpdate() throws Exception {
    String refName = aBranchRef();
    MultiSiteRefDatabase multiSiteRefDb =
        new MultiSiteRefDatabase(
            refUpdateFactoryMock, refBatchUpdateFactoryMock, A_TEST_PROJECT_NAME, refDatabaseMock);
    doReturn(refUpdateMock).when(refDatabaseMock).newUpdate(refName, false);

    multiSiteRefDb.newUpdate(refName, false);

    verify(refUpdateFactoryMock).create(A_TEST_PROJECT_NAME, refUpdateMock, refDatabaseMock);
  }
}
