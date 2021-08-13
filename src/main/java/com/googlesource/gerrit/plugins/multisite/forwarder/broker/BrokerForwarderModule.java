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

package com.googlesource.gerrit.plugins.multisite.forwarder.broker;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheEvictionForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.IndexEventForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.ProjectListUpdateForwarder;

public class BrokerForwarderModule extends LifecycleModule {
  @Override
  protected void configure() {
    DynamicSet.bind(binder(), IndexEventForwarder.class).to(BrokerIndexEventForwarder.class);
    DynamicSet.bind(binder(), CacheEvictionForwarder.class).to(BrokerCacheEvictionForwarder.class);
    DynamicSet.bind(binder(), ProjectListUpdateForwarder.class)
        .to(BrokerProjectListUpdateForwarder.class);
  }
}
