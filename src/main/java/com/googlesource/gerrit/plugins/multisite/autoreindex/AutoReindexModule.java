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

package com.googlesource.gerrit.plugins.multisite.autoreindex;

import com.google.gerrit.extensions.events.AccountIndexedListener;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.events.GroupIndexedListener;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.AbstractModule;

public class AutoReindexModule extends AbstractModule {

  @Override
  protected void configure() {
    DynamicSet.bind(binder(), LifecycleListener.class).to(AutoReindexScheduler.class);
    DynamicSet.bind(binder(), ChangeIndexedListener.class).to(IndexTs.class);
    DynamicSet.bind(binder(), AccountIndexedListener.class).to(IndexTs.class);
    DynamicSet.bind(binder(), GroupIndexedListener.class).to(IndexTs.class);
  }
}
