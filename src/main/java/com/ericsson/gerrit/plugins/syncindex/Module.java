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

package com.ericsson.gerrit.plugins.syncindex;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.extensions.events.ChangeIndexedListener;
import com.google.inject.Provides;
import com.google.inject.Scopes;

import org.apache.http.impl.client.CloseableHttpClient;

import java.util.concurrent.Executor;

class Module extends LifecycleModule {

  @Override
  protected void configure() {
    bind(CloseableHttpClient.class).toProvider(HttpClientProvider.class)
        .in(Scopes.SINGLETON);
    bind(Configuration.class).in(Scopes.SINGLETON);
    bind(HttpSession.class);
    bind(RestSession.class);
    bind(Executor.class)
        .annotatedWith(SyncIndexExecutor.class)
        .toProvider(SyncIndexExecutorProvider.class);
    listener().to(SyncIndexExecutorProvider.class);
    DynamicSet.bind(binder(), ChangeIndexedListener.class).to(
        IndexEventHandler.class);
  }

  @Provides
  @SyncUrl
  String syncUrl(Configuration config) {
    return config.getUrl();
  }
}
