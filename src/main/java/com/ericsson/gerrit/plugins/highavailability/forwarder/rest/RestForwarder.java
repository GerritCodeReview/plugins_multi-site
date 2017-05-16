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

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.HttpResponseHandler.HttpResult;
import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.SupplierSerializer;
import com.google.gson.GsonBuilder;
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
  public boolean indexAccount(final int accountId) {
    return new Request("index account " + accountId) {
      @Override
      HttpResult send() throws IOException {
        return httpSession.post(
            Joiner.on("/").join(pluginRelativePath, "index/account", accountId));
      }
    }.execute();
  }

  @Override
  public boolean indexChange(final int changeId) {
    return new Request("index change " + changeId) {
      @Override
      HttpResult send() throws IOException {
        return httpSession.post(buildIndexEndpoint(changeId));
      }
    }.execute();
  }

  @Override
  public boolean deleteChangeFromIndex(final int changeId) {
    return new Request("delete change " + changeId + " from index") {
      @Override
      HttpResult send() throws IOException {
        return httpSession.delete(buildIndexEndpoint(changeId));
      }
    }.execute();
  }

  private String buildIndexEndpoint(int changeId) {
    return Joiner.on("/").join(pluginRelativePath, "index/change", changeId);
  }

  @Override
  public boolean send(final Event event) {
    return new Request("send event " + event.type) {
      @Override
      HttpResult send() throws IOException {
        String serializedEvent =
            new GsonBuilder()
                .registerTypeAdapter(Supplier.class, new SupplierSerializer())
                .create()
                .toJson(event);
        return httpSession.post(Joiner.on("/").join(pluginRelativePath, "event"), serializedEvent);
      }
    }.execute();
  }

  @Override
  public boolean evict(final String cacheName, final Object key) {
    return new Request("evict for cache " + cacheName + "[" + key + "]") {
      @Override
      HttpResult send() throws IOException {
        String json = GsonParser.toJson(cacheName, key);
        return httpSession.post(Joiner.on("/").join(pluginRelativePath, "cache", cacheName), json);
      }
    }.execute();
  }

  private abstract class Request {
    private String name;
    private int execCnt;

    Request(String name) {
      this.name = name;
    }

    boolean execute() {
      for (; ; ) {
        try {
          execCnt++;
          tryOnce();
          return true;
        } catch (ForwardingException e) {
          if (!e.isRecoverable()) {
            log.error("Failed to {}", name, e);
            return false;
          }
          if (execCnt >= cfg.getMaxTries()) {
            log.error("Failed to {}, after {} tries", name, cfg.getMaxTries());
            return false;
          }

          logRetry(e);
          try {
            Thread.sleep(cfg.getRetryInterval());
          } catch (InterruptedException ie) {
            log.error("{} was interrupted, giving up", name, ie);
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
          throw new ForwardingException(true, "Unable to " + name + ": " + result.getMessage());
        }
      } catch (IOException e) {
        throw new ForwardingException(isRecoverable(e), e.getMessage(), e);
      }
    }

    abstract HttpResult send() throws IOException;

    boolean isRecoverable(IOException e) {
      return !(e instanceof SSLException);
    }

    void logRetry(Throwable cause) {
      if (log.isDebugEnabled()) {
        log.debug("Retrying to {} caused by '{}'", name, cause);
      }
    }
  }
}
