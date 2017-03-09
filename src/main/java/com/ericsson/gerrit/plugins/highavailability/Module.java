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

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

import com.ericsson.gerrit.plugins.highavailability.cache.CacheModule;
import com.ericsson.gerrit.plugins.highavailability.event.EventModule;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.RestForwarderModule;
import com.ericsson.gerrit.plugins.highavailability.index.IndexModule;
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfoModule;

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
}
