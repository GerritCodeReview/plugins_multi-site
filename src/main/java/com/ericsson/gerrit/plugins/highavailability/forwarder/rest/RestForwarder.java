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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.SupplierSerializer;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.HttpResponseHandler.HttpResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

class RestForwarder implements Forwarder {
  private static final Logger log =
      LoggerFactory.getLogger(RestForwarder.class);

  private final HttpSession httpSession;
  private final String pluginRelativePath;

  @Inject
  RestForwarder(HttpSession httpClient,
      @PluginName String pluginName) {
    this.httpSession = httpClient;
    this.pluginRelativePath = Joiner.on("/").join("/plugins", pluginName);
  }

  @Override
  public boolean indexChange(int changeId) {
    try {
      HttpResult result = httpSession.post(buildIndexEndpoint(changeId));
      if (result.isSuccessful()) {
        return true;
      }
      log.error("Unable to index change {}. Cause: {}", changeId,
          result.getMessage());
    } catch (IOException e) {
      log.error("Error trying to index change " + changeId, e);
    }
    return false;
  }

  @Override
  public boolean deleteChangeFromIndex(int changeId) {
    try {
      HttpResult result = httpSession.delete(buildIndexEndpoint(changeId));
      if (result.isSuccessful()) {
        return true;
      }
      log.error("Unable to delete from index change {}. Cause: {}", changeId,
          result.getMessage());
    } catch (IOException e) {
      log.error("Error trying to delete from index change " + changeId, e);
    }
    return false;
  }

  private String buildIndexEndpoint(int changeId) {
    return Joiner.on("/").join(pluginRelativePath, "index", changeId);
  }

  @Override
  public boolean send(Event event) {
    String serializedEvent = new GsonBuilder()
        .registerTypeAdapter(Supplier.class, new SupplierSerializer()).create()
        .toJson(event);
    try {
      HttpResult result = httpSession.post(
          Joiner.on("/").join(pluginRelativePath, "event"), serializedEvent);
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

  @Override
  public boolean evict(String cacheName, Object key) {
    try {
      String json = GsonParser.toJson(cacheName, key);
      return httpSession
          .post(Joiner.on("/").join(pluginRelativePath, "cache", cacheName),
              json)
          .isSuccessful();
    } catch (IOException e) {
      log.error("Error trying to evict for cache " + cacheName, e);
      return false;
    }
  }
}
