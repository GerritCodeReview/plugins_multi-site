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

package com.googlesource.gerrit.plugins.multisite.forwarder.rest;

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.restapi.Url;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.IndexEventForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.AccountIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.GroupIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectIndexEvent;
import com.googlesource.gerrit.plugins.multisite.peers.PeerInfo;
import java.util.Set;

@Singleton
public class RestIndexEventForwarder extends AbstractRestForwarder implements IndexEventForwarder {
  @Inject
  RestIndexEventForwarder(
      HttpSession httpClient,
      @PluginName String pluginName,
      Configuration cfg,
      Provider<Set<PeerInfo>> peerInfoProvider) {
    super(httpClient, pluginName, cfg, peerInfoProvider);
  }

  @Override
  public boolean indexAccount(AccountIndexEvent event) {
    return post("index account", "index/account", event.accountId, event);
  }

  @Override
  public boolean indexChange(ChangeIndexEvent event) {
    return post(
        "index change",
        "index/change",
        Url.encode(event.projectName) + "~" + event.changeId,
        event);
  }

  @Override
  public boolean deleteChangeFromIndex(ChangeIndexEvent event) {
    return delete("delete change", "index/change", "~" + event.changeId, event);
  }

  @Override
  public boolean indexGroup(GroupIndexEvent event) {
    return post("index group", "index/group", event.groupUUID, event);
  }

  @Override
  public boolean indexProject(ProjectIndexEvent event) {
    return post("index project", "index/project", Url.encode(event.projectName), event);
  }
}
