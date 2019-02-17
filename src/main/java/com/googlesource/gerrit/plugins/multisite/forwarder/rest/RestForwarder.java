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

package com.googlesource.gerrit.plugins.multisite.forwarder.rest;

import com.google.common.base.Joiner;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.cache.Constants;
import com.googlesource.gerrit.plugins.multisite.forwarder.Forwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectListUpdateEvent;
import com.googlesource.gerrit.plugins.multisite.peers.PeerInfo;
import java.util.Set;

@Singleton
class RestForwarder extends AbstractRestForwarder implements Forwarder {

  @Inject
  RestForwarder(
      HttpSession httpClient,
      @PluginName String pluginName,
      Configuration cfg,
      Provider<Set<PeerInfo>> peerInfoProvider) {
    super(httpClient, pluginName, cfg, peerInfoProvider);
  }

  @Override
  public boolean send(final Event event) {
    return post("send event", "event", event.type, event);
  }

  @Override
  public boolean updateProjectList(ProjectListUpdateEvent event) {
    return execute(
        event.remove ? RequestMethod.DELETE : RequestMethod.POST,
        String.format("Update project_list, %s ", event.remove ? "remove" : "add"),
        buildProjectListEndpoint(),
        Url.encode(event.projectName));
  }

  private static String buildProjectListEndpoint() {
    return Joiner.on("/").join("cache", Constants.PROJECT_LIST);
  }
}
