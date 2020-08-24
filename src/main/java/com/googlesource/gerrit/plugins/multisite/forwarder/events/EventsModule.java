// Copyright (C) 2020 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.forwarder.events;

import com.google.gerrit.extensions.config.FactoryModule;

public class EventsModule extends FactoryModule {

  @Override
  protected void configure() {
    factory(CacheEvictionEvent.Factory.class);
    factory(AccountIndexEvent.Factory.class);
    factory(GroupIndexEvent.Factory.class);
    factory(ProjectIndexEvent.Factory.class);
    factory(ChangeIndexEvent.Factory.class);
    factory(ProjectListUpdateEvent.Factory.class);
  }
}
