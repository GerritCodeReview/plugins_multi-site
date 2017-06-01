// Copyright (C) 2014 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.websession.file;

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.servlet.RequestScoped;
import com.google.inject.servlet.ServletScopes;

public class FileBasedWebsessionModule extends LifecycleModule {
  @Override
  protected void configure() {
    bindScope(RequestScoped.class, ServletScopes.REQUEST);
    DynamicItem.bind(binder(), WebSession.class)
        .to(FileBasedWebSession.class)
        .in(RequestScoped.class);
    listener().to(FileBasedWebSessionCacheCleaner.class);
  }
}
