// Copyright (C) 2017 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.forwarder.rest;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.googlesource.gerrit.plugins.multisite.Configuration.Http;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheEvictionForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.IndexEventForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import com.googlesource.gerrit.plugins.multisite.forwarder.ProjectListUpdateForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.StreamEventForwarder;
import org.apache.http.impl.client.CloseableHttpClient;

public class RestForwarderModule extends AbstractModule {
  private final Http http;

  public RestForwarderModule(Http http) {
    this.http = http;
  }

  @Override
  protected void configure() {
    bind(CloseableHttpClient.class).toProvider(HttpClientProvider.class).in(Scopes.SINGLETON);
    bind(HttpSession.class);
    DynamicSet.bind(binder(), ProjectListUpdateForwarder.class).to(RestProjectListUpdateForwarder.class);
    if (http.enabledEvent(EventFamily.INDEX_EVENT)) {
      DynamicSet.bind(binder(), IndexEventForwarder.class).to(RestIndexEventForwarder.class);
    }
    if (http.enabledEvent(EventFamily.CACHE_EVENT)) {
      DynamicSet.bind(binder(), CacheEvictionForwarder.class).to(RestCacheEvictionForwarder.class);
    }
    if (http.enabledEvent(EventFamily.STREAM_EVENT)) {
      DynamicSet.bind(binder(), ProjectListUpdateForwarder.class)
          .to(RestProjectListUpdateForwarder.class);
    }
    if (http.enabledEvent(EventFamily.STREAM_EVENT)) {
      DynamicSet.bind(binder(), StreamEventForwarder.class).to(RestStreamEventForwarder.class);
    }
  }
}
