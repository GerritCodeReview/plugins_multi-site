// Copyright (C) 2022 The Android Open Source Project
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

import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.RepositoryConfig;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.git.MultiBaseLocalDiskRepositoryManager;

class RepositoryManagerModule extends LifecycleModule {
  private final RepositoryConfig cfg;

  RepositoryManagerModule(RepositoryConfig cfg) {
    this.cfg = cfg;
  }

  @Override
  protected void configure() {
    install(new RepositoryManagerModule(cfg));

    // part responsible for physical repositories handling
    listener().to(LocalDiskRepositoryManager.Lifecycle.class);
    if (!cfg.getAllBasePaths().isEmpty()) {
      bind(LocalDiskRepositoryManager.class).to(MultiBaseLocalDiskRepositoryManager.class);
    }
  }
}
