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
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.git.RepositoryCaseMismatchException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.SortedSet;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class MultiSiteGitRepositoryManager implements GitRepositoryManager {

  private final GitRepositoryManager gitRepositoryManager;

  @Inject MultiSiteRepository.Factory multiSiteRepoFactory;

  @Inject
  public MultiSiteGitRepositoryManager(
      LocalDiskRepositoryManager localDiskRepositoryManager) {
    this.gitRepositoryManager = localDiskRepositoryManager;
  }

  public MultiSiteGitRepositoryManager(GitRepositoryManager gitRepositoryManager) {
    this.gitRepositoryManager = gitRepositoryManager;
  }

  @Override
  public Repository openRepository(NameKey name) throws RepositoryNotFoundException, IOException {
    Repository openRepository = gitRepositoryManager.openRepository(name);

    return multiSiteRepoFactory.create(name.get(), openRepository);
  }

  @Override
  public Repository createRepository(NameKey name)
      throws RepositoryCaseMismatchException, RepositoryNotFoundException, IOException {
    Repository createdRepository = gitRepositoryManager.createRepository(name);

    return multiSiteRepoFactory.create(name.get(), createdRepository);
  }

  @Override
  public SortedSet<NameKey> list() {
    return gitRepositoryManager.list();
  }
}
