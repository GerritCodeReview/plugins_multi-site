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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.RefFixture;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RunWith(MockitoJUnitRunner.class)
public class ProjectVersionRefUpdateTest implements RefFixture {

  @Rule public InMemoryTestEnvironment testEnvironment = new InMemoryTestEnvironment();

  @Mock RefUpdatedEvent refUpdatedEvent;

  @Inject private ProjectConfig.Factory projectConfigFactory;
  @Inject private InMemoryRepositoryManager repoManager;

  private TestRepository<InMemoryRepository> repo;
  private ProjectConfig project;

  @Before
  public void setUp()
  throws Exception {
    InMemoryRepository inMemoryRepo = repoManager.createRepository(A_TEST_PROJECT_NAME_KEY);
    project = projectConfigFactory.create(A_TEST_PROJECT_NAME_KEY);
    project.load(inMemoryRepo);
    repo = new TestRepository<>(inMemoryRepo);
    repo.branch("master").commit().create();
  }

  @Test
  public void shouldUpdateProjectVersion() throws IOException {
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(A_TEST_PROJECT_NAME_KEY);
    refUpdatedEvent.eventCreatedOn = 10L;

    new ProjectVersionRefUpdate(repoManager).onEvent(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_REF);
    assertThat(ref).isNotNull();

    ObjectLoader loader = repo.getRepository().open(ref.getObjectId());
    String storedVersion = IOUtils.toString(loader.openStream(), StandardCharsets.UTF_8.name());
    assertThat(Long.parseLong(storedVersion)).isEqualTo(refUpdatedEvent.eventCreatedOn);
  }

  @Test
  public void shouldNotUpdateProjectVersionWhenProjectDoesntExist() throws IOException {
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(new Project.NameKey("aNonExistentProject"));

    new ProjectVersionRefUpdate(repoManager).onEvent(refUpdatedEvent);

    Ref  ref = repo.getRepository().findRef(ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_REF);
    assertThat(ref).isNull();
  }
}
