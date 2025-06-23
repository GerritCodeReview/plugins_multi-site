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

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.events.EventListener;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexAccountHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexChangeHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexGroupHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexProjectHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexingHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.AccountIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.GroupIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.IndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectIndexEvent;

public class RouterModule extends LifecycleModule {

  private final Configuration.Index indexConfig;

  @Inject
  public RouterModule(Configuration.Index indexConfig) {
    this.indexConfig = indexConfig;
  }

  public static final TypeLiteral<ForwardedIndexingHandler<?, ? extends IndexEvent>> INDEX_HANDLER =
      new TypeLiteral<>() {};

  @Override
  protected void configure() {
    bind(IndexEventRouter.class).in(Scopes.SINGLETON);
    listener().to(IndexEventRouter.class).in(Scopes.SINGLETON);
    DynamicSet.bind(binder(), EventListener.class).to(IndexEventRouter.class);
    DynamicMap.mapOf(binder(), INDEX_HANDLER);

    if (indexConfig.shouldIndex(ChangeIndexEvent.TYPE)) {
      bind(INDEX_HANDLER)
          .annotatedWith(Exports.named(ChangeIndexEvent.TYPE))
          .to(ForwardedIndexChangeHandler.class);
    }

    if (indexConfig.shouldIndex(GroupIndexEvent.TYPE)) {
      bind(INDEX_HANDLER)
          .annotatedWith(Exports.named(GroupIndexEvent.TYPE))
          .to(ForwardedIndexGroupHandler.class);
    }

    if (indexConfig.shouldIndex(ProjectIndexEvent.TYPE)) {
      bind(INDEX_HANDLER)
          .annotatedWith(Exports.named(ProjectIndexEvent.TYPE))
          .to(ForwardedIndexProjectHandler.class);
    }

    if (indexConfig.shouldIndex(AccountIndexEvent.TYPE)) {
      bind(INDEX_HANDLER)
          .annotatedWith(Exports.named(AccountIndexEvent.TYPE))
          .to(ForwardedIndexAccountHandler.class);
    }

    bind(CacheEvictionEventRouter.class).in(Scopes.SINGLETON);
    bind(ProjectListUpdateRouter.class).in(Scopes.SINGLETON);
    bind(StreamEventRouter.class).in(Scopes.SINGLETON);
  }
}
