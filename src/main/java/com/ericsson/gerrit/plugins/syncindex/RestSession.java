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

package com.ericsson.gerrit.plugins.syncindex;

import com.google.common.base.Joiner;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.inject.Inject;

import com.ericsson.gerrit.plugins.syncindex.IndexResponseHandler.IndexResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

class RestSession {
  private static final Logger log = LoggerFactory.getLogger(RestSession.class);

  private final HttpSession httpSession;
  private final String pluginName;

  @Inject
  RestSession(HttpSession httpClient, @PluginName String pluginName) {
    this.httpSession = httpClient;
    this.pluginName = pluginName;
  }

  boolean index(int changeId) {
    try {
      IndexResult result = httpSession.post(buildEndpoint(changeId));
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

  boolean deleteFromIndex(int changeId) {
    try {
      IndexResult result = httpSession.delete(buildEndpoint(changeId));
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

  private String buildEndpoint(int changeId) {
    return Joiner.on("/").join("/plugins", pluginName, "index", changeId);
  }
}
