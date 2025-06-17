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

package com.googlesource.gerrit.plugins.multisite.forwarder.router;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.events.EventListener;
import com.google.gerrit.server.events.EventTypes;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexChangeHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexingHandlerWithRetries;
import com.googlesource.gerrit.plugins.multisite.forwarder.NoopForwardedIndexChangeHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;

public class RouterModule extends LifecycleModule {
  @Override
  protected void configure() {



    bind(IndexEventRouter.class).in(Scopes.SINGLETON);
    listener().to(IndexEventRouter.class).in(Scopes.SINGLETON);
    DynamicSet.bind(binder(), EventListener.class).to(IndexEventRouter.class);

    boolean pullReplicationInstalled =  EventTypes.getRegisteredEvents().containsKey("fetch-ref-replication-done");

    if (pullReplicationInstalled) {
      bind(new TypeLiteral<ForwardedIndexingHandlerWithRetries<String, ChangeIndexEvent>>() {})
          .to(NoopForwardedIndexChangeHandler.class);
    } else {
      bind(new TypeLiteral<ForwardedIndexingHandlerWithRetries<String, ChangeIndexEvent>>() {})
          .to(ForwardedIndexChangeHandler.class);
    }

    bind(CacheEvictionEventRouter.class).in(Scopes.SINGLETON);
    bind(ProjectListUpdateRouter.class).in(Scopes.SINGLETON);
    bind(StreamEventRouter.class).in(Scopes.SINGLETON);
  }
}
