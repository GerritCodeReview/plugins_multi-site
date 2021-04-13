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

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.git.RepositoryCaseMismatchException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.SortedSet;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class MultiSiteGitRepositoryManager implements GitRepositoryManager {
  private final GitRepositoryManager gitRepositoryManager;
  private final MultiSiteRepository.Factory multiSiteRepoFactory;

  @Inject
  public MultiSiteGitRepositoryManager(
      MultiSiteRepository.Factory multiSiteRepoFactory,
      LocalDiskRepositoryManager localDiskRepositoryManager) {
    this.multiSiteRepoFactory = multiSiteRepoFactory;
    this.gitRepositoryManager = localDiskRepositoryManager;
  }

  @Override
  public Repository openRepository(Project.NameKey name)
      throws RepositoryNotFoundException, IOException {
    return wrap(name, gitRepositoryManager.openRepository(name));
  }

  @Override
  public Repository createRepository(Project.NameKey name)
      throws RepositoryCaseMismatchException, RepositoryNotFoundException, IOException {
    return wrap(name, gitRepositoryManager.createRepository(name));
  }

  @Override
  public Boolean canPerformGC() {
    return gitRepositoryManager.canPerformGC();
  }

  @Override
  public SortedSet<Project.NameKey> list() {
    return gitRepositoryManager.list();
  }

  private Repository wrap(Project.NameKey projectName, Repository projectRepo) {
    return multiSiteRepoFactory.create(projectName.get(), projectRepo);
  }
}
