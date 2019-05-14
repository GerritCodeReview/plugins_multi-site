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

package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper;

import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.googlesource.gerrit.plugins.multisite.ZookeeperConfig;
import com.googlesource.gerrit.plugins.multisite.validation.ProjectDeletedSharedDbCleanup;
import com.googlesource.gerrit.plugins.multisite.validation.ZkConnectionConfig;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.CustomSharedRefEnforcementByProject;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DefaultSharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement;
import org.apache.curator.framework.CuratorFramework;

public class ZkValidationModule extends AbstractModule {

  private ZookeeperConfig cfg;

  public ZkValidationModule(ZookeeperConfig cfg) {
    this.cfg = cfg;
  }

  @Override
  protected void configure() {

    if (cfg.getEnforcementRules().isEmpty()) {
      bind(SharedRefEnforcement.class).to(DefaultSharedRefEnforcement.class).in(Scopes.SINGLETON);
    } else {
      bind(SharedRefEnforcement.class)
          .to(CustomSharedRefEnforcementByProject.class)
          .in(Scopes.SINGLETON);
    }

    bind(SharedRefDatabase.class).to(ZkSharedRefDatabase.class);
    bind(CuratorFramework.class).toInstance(cfg.buildCurator());

    bind(ZkConnectionConfig.class)
        .toInstance(
            new ZkConnectionConfig(cfg.buildCasRetryPolicy(), cfg.getZkInterProcessLockTimeOut()));

    DynamicSet.bind(binder(), ProjectDeletedListener.class).to(ProjectDeletedSharedDbCleanup.class);
  }
}
