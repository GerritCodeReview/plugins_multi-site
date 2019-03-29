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

import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RepositoryCaseMismatchException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.SortedSet;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class MultiSiteGitRepositoryManager implements GitRepositoryManager {

  private final GitRepositoryManager gitRepositoryManager;
  private final MultiSiteRepository.Factory multiSiteRepoFactory;

  // FIXME: Not sure this is the best way to get the Repository Builder
  private BaseRepositoryBuilder returnRepoBuilderFromRepo(Repository repository) {
    BaseRepositoryBuilder builder;
    if (repository.getClass().isAssignableFrom(FileRepository.class)) {
      builder = new FileRepositoryBuilder();
    } else if (repository.getClass().isAssignableFrom(InMemoryRepository.class)) {
      builder = new InMemoryRepository.Builder();
    } else {
      throw new IllegalArgumentException(
          String.format(
              "Repository %s, not assignable from InMemoryRepository or FileRepository",
              repository.getClass()));
    }
    return builder;
  }

  @Inject
  public MultiSiteGitRepositoryManager(
      MultiSiteRepository.Factory multiSiteRepoFactory, GitRepositoryManager gitRepositoryManager) {
    this.gitRepositoryManager = gitRepositoryManager;
    this.multiSiteRepoFactory = multiSiteRepoFactory;
  }

  @Override
  public Repository openRepository(NameKey name) throws RepositoryNotFoundException, IOException {
    Repository openRepository = gitRepositoryManager.openRepository(name);

    return multiSiteRepoFactory.create(
        name.get(), openRepository, returnRepoBuilderFromRepo(openRepository));
  }

  @Override
  public Repository createRepository(NameKey name)
      throws RepositoryCaseMismatchException, RepositoryNotFoundException, IOException {
    Repository createdRepository = gitRepositoryManager.createRepository(name);

    return multiSiteRepoFactory.create(
        name.get(), createdRepository, returnRepoBuilderFromRepo(createdRepository));
  }

  @Override
  public SortedSet<NameKey> list() {
    return gitRepositoryManager.list();
  }
}
