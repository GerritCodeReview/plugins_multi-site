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

import com.ericsson.gerrit.plugins.highavailability.cache.CacheModule;
import com.ericsson.gerrit.plugins.highavailability.event.EventModule;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.RestForwarderModule;
import com.ericsson.gerrit.plugins.highavailability.index.IndexModule;
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfoModule;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class Module extends AbstractModule {

  @Override
  protected void configure() {
    bind(Configuration.class).in(Scopes.SINGLETON);
    install(new RestForwarderModule());
    install(new EventModule());
    install(new IndexModule());
    install(new CacheModule());
    install(new PeerInfoModule());
  }

  @Provides
  @Singleton
  @SharedDirectory
  Path getSharedDirectory(PluginConfigFactory cfg, @PluginName String pluginName)
      throws IOException {
    String sharedDirectory =
        Strings.emptyToNull(cfg.getFromGerritConfig(pluginName, true).getString("sharedDirectory"));
    if (sharedDirectory == null) {
      throw new ProvisionException("sharedDirectory must be configured");
    }
    Path sharedDirectoryPath = Paths.get(sharedDirectory);
    Files.createDirectories(sharedDirectoryPath);
    return sharedDirectoryPath;
  }
}
