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

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Scopes;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DefaultSharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.ZkValidationModule;

public class ValidationModule extends FactoryModule {
  private final Configuration cfg;
  private final boolean disableGitRepositoryValidation;

  public ValidationModule(Configuration cfg, boolean disableGitRepositoryValidation) {
    this.cfg = cfg;
    this.disableGitRepositoryValidation = disableGitRepositoryValidation;
  }

  @Override
  protected void configure() {
    factory(MultiSiteRepository.Factory.class);
    factory(MultiSiteRefDatabase.Factory.class);
    factory(MultiSiteRefUpdate.Factory.class);
    factory(MultiSiteBatchRefUpdate.Factory.class);
    factory(RefUpdateValidator.Factory.class);
    factory(BatchRefUpdateValidator.Factory.class);

    if (!disableGitRepositoryValidation) {
      bind(GitRepositoryManager.class).to(MultiSiteGitRepositoryManager.class);
    }

    bind(SharedRefEnforcement.class).to(DefaultSharedRefEnforcement.class).in(Scopes.SINGLETON);
    install(new ZkValidationModule(cfg));
  }
}
