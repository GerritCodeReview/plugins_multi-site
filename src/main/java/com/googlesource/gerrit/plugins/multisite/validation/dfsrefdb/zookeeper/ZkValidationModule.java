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
import com.google.inject.name.Names;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.validation.ProjectDeletedSharedDbCleanup;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;

public class ZkValidationModule extends AbstractModule {

  private Configuration cfg;

  public ZkValidationModule(Configuration cfg) {
    this.cfg = cfg;
  }

  @Override
  protected void configure() {
    bind(SharedRefDatabase.class).to(ZkSharedRefDatabase.class);
    bind(CuratorFramework.class).toInstance(cfg.getZookeeperConfig().buildCurator());
    bind(RetryPolicy.class)
        .annotatedWith(Names.named("ZkLockRetryPolicy"))
        .toInstance(cfg.getZookeeperConfig().buildCasRetryPolicy());

    DynamicSet.bind(binder(), ProjectDeletedListener.class).to(ProjectDeletedSharedDbCleanup.class);
  }
}
