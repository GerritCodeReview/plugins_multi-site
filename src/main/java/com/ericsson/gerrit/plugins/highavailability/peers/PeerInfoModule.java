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

package com.ericsson.gerrit.plugins.highavailability.peers;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.peers.jgroups.JGroupsPeerInfoProvider;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.TypeLiteral;
import java.util.Set;

public class PeerInfoModule extends LifecycleModule {

  private final Configuration.PeerInfoStrategy strategy;

  public PeerInfoModule(Configuration.PeerInfoStrategy strategy) {
    this.strategy = strategy;
  }

  @Override
  protected void configure() {
    switch (strategy) {
      case STATIC:
        bind(new TypeLiteral<Set<PeerInfo>>() {}).toProvider(PluginConfigPeerInfoProvider.class);
        break;
      case JGROUPS:
        bind(new TypeLiteral<Set<PeerInfo>>() {}).toProvider(JGroupsPeerInfoProvider.class);
        listener().to(JGroupsPeerInfoProvider.class);
        break;
      default:
        throw new IllegalArgumentException("Unsupported peer info strategy: " + strategy);
    }
  }
}
