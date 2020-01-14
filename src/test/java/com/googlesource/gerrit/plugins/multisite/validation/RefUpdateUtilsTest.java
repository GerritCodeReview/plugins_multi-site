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
import static org.junit.Assert.fail;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import java.util.Optional;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;

public class RefUpdateUtilsTest {

  @Rule public InMemoryTestEnvironment testEnvironment = new InMemoryTestEnvironment();
  @Rule public TemporaryFolder gitFolder = new TemporaryFolder();
  @Rule public TestName nameRule = new TestName();

  @Inject private ProjectConfig.Factory projectConfigFactory;

  private TestRepository<InMemoryRepository> repo;

  @Inject private InMemoryRepositoryManager repoManager;

  private ProjectConfig project;
  String testRepoName = "testRepo";
  RevCommit masterCommit;

  @Before
  public void setUp() throws Exception {
    Project.NameKey name = new Project.NameKey(testRepoName);
    InMemoryRepository inMemoryRepo = repoManager.createRepository(name);
    project = projectConfigFactory.create(name);
    project.load(inMemoryRepo);
    repo = new TestRepository<>(inMemoryRepo);
    masterCommit = repo.branch("master").commit().create();
  }

  @Test
  public void shouldReturnVersioningReceiveCommandWhenCreatingVersion() {
    Optional<ReceiveCommand> optionalReceiveCommand =
        RefUpdateUtils.getVersioningCommand(repoManager, testRepoName);
    assertThat(optionalReceiveCommand).isNotEqualTo(Optional.empty());
    assertThat(optionalReceiveCommand.get().getOldId()).isEqualTo(ObjectId.zeroId());
  }

  @Test
  public void shouldReturnVersioningReceiveCommandWhenUpdatingVersion() {
    try {
      repo.branch(RefUpdateUtils.MULTI_SITE_VERSIONING_REF).commit().parent(masterCommit).create();
    } catch (Exception e) {
      fail("What the fork! " + e.getMessage());
    }
    Optional<ReceiveCommand> optionalReceiveCommand =
        RefUpdateUtils.getVersioningCommand(repoManager, testRepoName);
    assertThat(optionalReceiveCommand).isNotEqualTo(Optional.empty());
    assertThat(optionalReceiveCommand.get().getOldId()).isNotEqualTo(ObjectId.zeroId());
  }

  @Test
  public void shouldReturnEmptyWhenProjectDoesntExist() {
    Optional<ReceiveCommand> optionalReceiveCommand =
        RefUpdateUtils.getVersioningCommand(repoManager, "nonExistentRepo");
    assertThat(optionalReceiveCommand).isEqualTo(Optional.empty());
  }
}
