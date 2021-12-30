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
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.config.RepositoryConfig;
import com.google.inject.Scopes;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.LockWrapper;
import com.googlesource.gerrit.plugins.multisite.Log4jProjectVersionLogger;
import com.googlesource.gerrit.plugins.multisite.Log4jSharedRefLogger;
import com.googlesource.gerrit.plugins.multisite.ProjectVersionLogger;
import com.googlesource.gerrit.plugins.multisite.SharedRefDatabaseWrapper;
import com.googlesource.gerrit.plugins.multisite.SharedRefLogger;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.CustomSharedRefEnforcementByProject;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DefaultSharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement;
import com.googlesource.gerrit.plugins.replication.ReplicationExtensionPointModule;
import com.googlesource.gerrit.plugins.replication.ReplicationPushFilter;

public class ValidationModule extends FactoryModule {
  private final Configuration cfg;
  private final RepositoryConfig repoConfig;

  public ValidationModule(Configuration cfg, RepositoryConfig repoConfig) {
    this.cfg = cfg;
    this.repoConfig = repoConfig;
  }

  @Override
  protected void configure() {
    install(new ReplicationExtensionPointModule());

    bind(SharedRefDatabaseWrapper.class).in(Scopes.SINGLETON);
    bind(SharedRefLogger.class).to(Log4jSharedRefLogger.class);
    bind(ProjectVersionLogger.class).to(Log4jProjectVersionLogger.class);
    factory(LockWrapper.Factory.class);

    factory(MultiSiteRepository.Factory.class);
    factory(MultiSiteRefDatabase.Factory.class);
    factory(MultiSiteRefUpdate.Factory.class);
    factory(MultiSiteBatchRefUpdate.Factory.class);
    factory(RefUpdateValidator.Factory.class);
    factory(BatchRefUpdateValidator.Factory.class);

    install(new RepositoryManagerModule(repoConfig));

    DynamicItem.bind(binder(), ReplicationPushFilter.class)
        .to(MultisiteReplicationPushFilter.class);

    if (cfg.getSharedRefDb().getEnforcementRules().isEmpty()) {
      bind(SharedRefEnforcement.class).to(DefaultSharedRefEnforcement.class).in(Scopes.SINGLETON);
    } else {
      bind(SharedRefEnforcement.class)
          .to(CustomSharedRefEnforcementByProject.class)
          .in(Scopes.SINGLETON);
    }
  }
}
