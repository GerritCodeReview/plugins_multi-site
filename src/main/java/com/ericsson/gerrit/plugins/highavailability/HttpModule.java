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

package com.ericsson.gerrit.plugins.highavailability;

import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.RestForwarderServletModule;
import com.ericsson.gerrit.plugins.highavailability.health.HealthServletModule;
import com.ericsson.gerrit.plugins.highavailability.websession.file.FileBasedWebsessionModule;
import com.google.gerrit.httpd.plugins.HttpPluginModule;
import com.google.inject.Inject;

class HttpModule extends HttpPluginModule {
  private final Configuration config;

  @Inject
  HttpModule(Configuration config) {
    this.config = config;
  }

  @Override
  protected void configureServlets() {
    install(new RestForwarderServletModule());
    install(new HealthServletModule());
    if (config.websession().synchronize()) {
      install(new FileBasedWebsessionModule());
    }
  }
}
