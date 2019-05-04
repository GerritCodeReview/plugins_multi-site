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

import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.RefFixture;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MultiSiteGitRepositoryManagerTest implements RefFixture {

  @Mock LocalDiskRepositoryManager localDiskRepositoryManagerMock;

  @Mock MultiSiteRepository.Factory multiSiteRepositoryFactoryMock;

  @Mock Repository repositoryMock;

  @Mock MultiSiteRepository multiSiteRepositoryMock;

  MultiSiteGitRepositoryManager msRepoMgr;

  @Override
  public String testBranch() {
    return "foo";
  }

  @Before
  public void setUp() throws Exception {
    doReturn(multiSiteRepositoryMock)
        .when(multiSiteRepositoryFactoryMock)
        .create(A_TEST_PROJECT_NAME, repositoryMock);
    msRepoMgr =
        new MultiSiteGitRepositoryManager(
            multiSiteRepositoryFactoryMock, localDiskRepositoryManagerMock);
  }

  @Test
  public void openRepositoryShouldCreateMultiSiteRepositoryWrapper() throws Exception {
    doReturn(repositoryMock)
        .when(localDiskRepositoryManagerMock)
        .openRepository(A_TEST_PROJECT_NAME_KEY);

    msRepoMgr.openRepository(A_TEST_PROJECT_NAME_KEY);

    verifyThatMultiSiteRepositoryWrapperHasBeenCreated();
  }

  @Test
  public void createRepositoryShouldCreateMultiSiteRepositoryWrapper() throws Exception {
    doReturn(repositoryMock)
        .when(localDiskRepositoryManagerMock)
        .createRepository(A_TEST_PROJECT_NAME_KEY);

    msRepoMgr.createRepository(A_TEST_PROJECT_NAME_KEY);

    verifyThatMultiSiteRepositoryWrapperHasBeenCreated();
  }

  private void verifyThatMultiSiteRepositoryWrapperHasBeenCreated() {
    verify(multiSiteRepositoryFactoryMock).create(A_TEST_PROJECT_NAME, repositoryMock);
  }
}
