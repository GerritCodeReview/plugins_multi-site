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

package com.googlesource.gerrit.plugins.multisite;

import com.google.gerrit.server.ModuleImpl;
import com.google.gerrit.server.config.RepositoryConfig;
import com.google.gerrit.server.git.GitRepositoryManagerModule;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.validation.ValidationModule;

@ModuleImpl(name = GitRepositoryManagerModule.MANAGER_MODULE)
public class GitModule extends AbstractModule {
  private final Configuration config;
  private final RepositoryConfig repoConfig;

  @Inject
  public GitModule(Configuration config, RepositoryConfig repoConfig) {
    this.config = config;
    this.repoConfig = repoConfig;
  }

  @Override
  protected void configure() {
    if (config.getSharedRefDb().isEnabled()) {
      install(new ValidationModule(config, repoConfig));
    } else {
      install(new GitRepositoryManagerModule(repoConfig));
    }
  }
}
