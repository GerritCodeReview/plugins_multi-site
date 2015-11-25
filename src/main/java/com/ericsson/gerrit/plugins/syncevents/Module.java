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

package com.ericsson.gerrit.plugins.syncevents;

import com.google.gerrit.common.EventDispatcher;
import com.google.gerrit.common.EventListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;

import org.apache.http.impl.client.CloseableHttpClient;

import java.util.concurrent.ScheduledThreadPoolExecutor;

class Module extends LifecycleModule {

  @Override
  protected void configure() {
    bind(CloseableHttpClient.class).toProvider(HttpClientProvider.class)
        .in(Scopes.SINGLETON);
    bind(Configuration.class);
    bind(HttpSession.class);
    bind(RestSession.class);
    bind(ScheduledThreadPoolExecutor.class)
        .annotatedWith(SyncEventExecutor.class)
        .toProvider(SyncEventExecutorProvider.class);
    listener().to(SyncEventExecutorProvider.class);
    DynamicSet.bind(binder(), EventListener.class).to(EventHandler.class);
    DynamicItem.bind(binder(), EventDispatcher.class).to(SyncEventBroker.class);
  }

  @Provides
  @SyncUrl
  String syncUrl(Configuration config) {
    return config.getUrl();
  }
}
