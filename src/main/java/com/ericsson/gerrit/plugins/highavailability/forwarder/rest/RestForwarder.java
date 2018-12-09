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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.cache.Constants;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.HttpResponseHandler.HttpResult;
import com.google.common.base.Joiner;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import java.io.IOException;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RestForwarder implements Forwarder {
  private static final Logger log = LoggerFactory.getLogger(RestForwarder.class);

  private final HttpSession httpSession;
  private final String pluginRelativePath;
  private final Configuration cfg;

  @Inject
  RestForwarder(HttpSession httpClient, @PluginName String pluginName, Configuration cfg) {
    this.httpSession = httpClient;
    this.pluginRelativePath = Joiner.on("/").join("/plugins", pluginName);
    this.cfg = cfg;
  }

  @Override
  public boolean indexAccount(final int accountId, IndexEvent event) {
    return new Request("index account", accountId) {
      @Override
      HttpResult send() throws IOException {
        return httpSession.post(
            Joiner.on("/").join(pluginRelativePath, "index/account", accountId), event);
      }
    }.execute();
  }

  @Override
  public boolean indexChange(String projectName, int changeId, IndexEvent event) {
    return new Request("index change", changeId) {
      @Override
      HttpResult send() throws IOException {
        return httpSession.post(buildIndexEndpoint(projectName, changeId), event);
      }
    }.execute();
  }

  @Override
  public boolean deleteChangeFromIndex(final int changeId, IndexEvent event) {
    return new Request("delete change", changeId) {
      @Override
      HttpResult send() throws IOException {
        return httpSession.delete(buildIndexEndpoint(changeId), event);
      }
    }.execute();
  }

  @Override
  public boolean indexGroup(final String uuid, IndexEvent event) {
    return new Request("index group", uuid) {
      @Override
      HttpResult send() throws IOException {
        return httpSession.post(
            Joiner.on("/").join(pluginRelativePath, "index/group", uuid), event);
      }
    }.execute();
  }

  private String buildIndexEndpoint(int changeId) {
    return buildIndexEndpoint("", changeId);
  }

  private String buildIndexEndpoint(String projectName, int changeId) {
    String escapedProjectName = Url.encode(projectName);
    return Joiner.on("/")
        .join(pluginRelativePath, "index/change", escapedProjectName + '~' + changeId);
  }

  @Override
  public boolean send(final Event event) {
    return new Request("send event", event.type) {
      @Override
      HttpResult send() throws IOException {
        return httpSession.post(Joiner.on("/").join(pluginRelativePath, "event"), event);
      }
    }.execute();
  }

  @Override
  public boolean evict(final String cacheName, final Object key) {
    return new Request("invalidate cache " + cacheName, key) {
      @Override
      HttpResult send() throws IOException {
        String json = GsonParser.toJson(cacheName, key);
        return httpSession.post(Joiner.on("/").join(pluginRelativePath, "cache", cacheName), json);
      }
    }.execute();
  }

  @Override
  public boolean addToProjectList(String projectName) {
    return new Request("Update project_list, add ", projectName) {
      @Override
      HttpResult send() throws IOException {
        return httpSession.post(buildProjectListEndpoint(projectName));
      }
    }.execute();
  }

  @Override
  public boolean removeFromProjectList(String projectName) {
    return new Request("Update project_list, remove ", projectName) {
      @Override
      HttpResult send() throws IOException {
        return httpSession.delete(buildProjectListEndpoint(projectName));
      }
    }.execute();
  }

  private String buildProjectListEndpoint(String projectName) {
    return Joiner.on("/")
        .join(pluginRelativePath, "cache", Constants.PROJECT_LIST, Url.encode(projectName));
  }

  private abstract class Request {
    private final String action;
    private final Object key;
    private int execCnt;

    Request(String action, Object key) {
      this.action = action;
      this.key = key;
    }

    boolean execute() {
      log.debug("Executing {} {}", action, key);
      for (; ; ) {
        try {
          execCnt++;
          tryOnce();
          log.debug("{} {} OK", action, key);
          return true;
        } catch (ForwardingException e) {
          int maxTries = cfg.http().maxTries();
          log.debug("Failed to {} {} [{}/{}]", action, key, execCnt, maxTries, e);
          if (!e.isRecoverable()) {
            log.error("{} {} failed with unrecoverable error; giving up", action, key, e);
            return false;
          }
          if (execCnt >= maxTries) {
            log.error("Failed to {} {} after {} tries; giving up", action, key, maxTries);
            return false;
          }

          log.debug("Retrying to {} {}", action, key);
          try {
            Thread.sleep(cfg.http().retryInterval());
          } catch (InterruptedException ie) {
            log.error("{} {} was interrupted; giving up", action, key, ie);
            Thread.currentThread().interrupt();
            return false;
          }
        }
      }
    }

    void tryOnce() throws ForwardingException {
      try {
        HttpResult result = send();
        if (!result.isSuccessful()) {
          throw new ForwardingException(
              true, String.format("Unable to %s %s : %s", action, key, result.getMessage()));
        }
      } catch (IOException e) {
        throw new ForwardingException(isRecoverable(e), e.getMessage(), e);
      }
    }

    abstract HttpResult send() throws IOException;

    boolean isRecoverable(IOException e) {
      return !(e instanceof SSLException);
    }
  }
}
