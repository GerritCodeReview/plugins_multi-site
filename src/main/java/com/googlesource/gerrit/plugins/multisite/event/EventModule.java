// Copyright (C) 2015 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.event;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.events.EventListener;
import java.util.concurrent.Executor;

public class EventModule extends LifecycleModule {

  @Override
  protected void configure() {
    bind(Executor.class).annotatedWith(EventExecutor.class).toProvider(EventExecutorProvider.class);
    listener().to(EventExecutorProvider.class);
    DynamicSet.bind(binder(), EventListener.class).to(EventHandler.class);
  }
}
