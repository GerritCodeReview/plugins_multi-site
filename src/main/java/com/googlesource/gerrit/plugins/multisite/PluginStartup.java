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

import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDatabaseWrapper;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import com.google.inject.Injector;

public class PluginStartup implements LifecycleListener {
  private SharedRefDatabaseWrapper sharedRefDb;
  private Injector injector;

  @Inject
  public PluginStartup(SharedRefDatabaseWrapper sharedRefDb, Injector injector) {
    this.sharedRefDb = sharedRefDb;
    this.injector = injector;
  }

  @Override
  public void start() {
    injector.injectMembers(sharedRefDb);
  }

  @Override
  public void stop() {}
}
