// Copyright (C) 2020 The Android Open Source Project
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

import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.RefFixture;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class ProjectVersionRefUpdateTest implements RefFixture  {

    @Rule public InMemoryTestEnvironment testEnvironment = new InMemoryTestEnvironment();

    @Mock GitReferenceUpdatedListener.Event event;

    @Inject private ProjectConfig.Factory projectConfigFactory;
    @Inject private InMemoryRepositoryManager repoManager;

    private TestRepository<InMemoryRepository> repo;
    private ProjectConfig project;
    private RevCommit masterCommit;


    @Before
    public void setUp() throws Exception {
        InMemoryRepository inMemoryRepo = repoManager.createRepository(A_TEST_PROJECT_NAME_KEY);
        project = projectConfigFactory.create(A_TEST_PROJECT_NAME_KEY);
        project.load(inMemoryRepo);
        repo = new TestRepository<>(inMemoryRepo);
        masterCommit = repo.branch("master").commit().create();
    }


    @Test
    public void shouldUpdateProjectVersion() {
        when(event.getProjectName()).thenReturn(A_TEST_PROJECT_NAME);
        when(event.getRefName()).thenReturn(A_REF_NAME_OF_A_PATCHSET);

        new ProjectVersionRefUpdate(repoManager).onGitReferenceUpdated(event);

        Ref ref;
        try {
            ref = repo.getRepository().findRef(ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_REF);
            assertThat(ref).isNotNull();
        } catch (Exception e) {
            fail("Should not raise any exception");
        }
    }

    @Test
    public void shouldNotUpdateProjectVersionWhenProjectDoesntExist() {
        when(event.getProjectName()).thenReturn("aNonExistingProject");
        when(event.getRefName()).thenReturn(A_REF_NAME_OF_A_PATCHSET);

        new ProjectVersionRefUpdate(repoManager).onGitReferenceUpdated(event);

        Ref ref;
        try {
            ref = repo.getRepository().findRef(ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_REF);
            assertThat(ref).isNull();
        } catch (Exception e) {
            fail("Should not raise any exception");
        }
    }
}
