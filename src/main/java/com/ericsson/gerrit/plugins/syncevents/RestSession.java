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

package com.ericsson.gerrit.plugins.syncevents;

import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.SupplierSerializer;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;

import com.ericsson.gerrit.plugins.syncevents.SyncEventsResponseHandler.SyncResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

class RestSession {
  private static final Logger log = LoggerFactory.getLogger(RestSession.class);
  private final HttpSession httpSession;
  private final String pluginName;

  @Inject
  RestSession(HttpSession httpClient,
      @PluginName String pluginName) {
    this.httpSession = httpClient;
    this.pluginName = pluginName;
  }

  boolean send(Event event) {
    String serializedEvent = new GsonBuilder()
        .registerTypeAdapter(Supplier.class, new SupplierSerializer()).create()
        .toJson(event);
    try {
      SyncResult result = httpSession.post(buildEndpoint(), serializedEvent);
      if (result.isSuccessful()) {
        return true;
      }
      log.error(
          "Unable to send event '" + event.type + "' " + result.getMessage());
    } catch (IOException e) {
      log.error("Error trying to send event " + event.type, e);
    }
    return false;
  }

  private String buildEndpoint() {
    return Joiner.on("/").join("/plugins", pluginName, "event");
  }
}
