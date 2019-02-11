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

package com.googlesource.gerrit.plugins.multisite;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.autoreindex.AutoReindexModule;
import com.googlesource.gerrit.plugins.multisite.cache.CacheModule;
import com.googlesource.gerrit.plugins.multisite.event.EventModule;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwarderModule;
import com.googlesource.gerrit.plugins.multisite.forwarder.rest.RestForwarderModule;
import com.googlesource.gerrit.plugins.multisite.index.IndexModule;
import com.googlesource.gerrit.plugins.multisite.peers.PeerInfoModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class Module extends AbstractModule {
  private final Configuration config;

  @Inject
  Module(Configuration config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    install(new ForwarderModule());
    install(new RestForwarderModule());

    if (config.cache().synchronize()) {
      install(new CacheModule());
    }
    if (config.event().synchronize()) {
      install(new EventModule());
    }
    if (config.index().synchronize()) {
      install(new IndexModule());
    }
    if (config.autoReindex().enabled()) {
      install(new AutoReindexModule());
    }
    install(new PeerInfoModule(config.peerInfo().strategy()));
  }

  @Provides
  @Singleton
  @SharedDirectory
  Path getSharedDirectory() throws IOException {
    Path sharedDirectoryPath = config.main().sharedDirectory();
    Files.createDirectories(sharedDirectoryPath);
    return sharedDirectoryPath;
  }
}
