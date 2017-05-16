// Copyright (C) 2015 Ericsson
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

package com.ericsson.gerrit.plugins.highavailability.cache;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.cache.CacheRemovalListener;
import java.util.concurrent.Executor;

public class CacheModule extends LifecycleModule {

  @Override
  protected void configure() {
    bind(Executor.class).annotatedWith(CacheExecutor.class).toProvider(CacheExecutorProvider.class);
    listener().to(CacheExecutorProvider.class);
    DynamicSet.bind(binder(), CacheRemovalListener.class).to(CacheEvictionHandler.class);
  }
}
