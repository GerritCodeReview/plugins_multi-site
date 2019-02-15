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
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.cache.Constants;
import com.googlesource.gerrit.plugins.multisite.forwarder.Forwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.AccountIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.CacheEvictionEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.GroupIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectListUpdateEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.rest.HttpResponseHandler.HttpResult;
import com.googlesource.gerrit.plugins.multisite.peers.PeerInfo;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.net.ssl.SSLException;
import org.apache.http.HttpException;
import org.apache.http.client.ClientProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RestForwarder implements Forwarder {
  enum RequestMethod {
    POST,
    DELETE
  }

  private static final Logger log = LoggerFactory.getLogger(RestForwarder.class);

  private final HttpSession httpSession;
  private final String pluginRelativePath;
  private final Configuration cfg;
  private final Provider<Set<PeerInfo>> peerInfoProvider;

  @Inject
  RestForwarder(
      HttpSession httpClient,
      @PluginName String pluginName,
      Configuration cfg,
      Provider<Set<PeerInfo>> peerInfoProvider) {
    this.httpSession = httpClient;
    this.pluginRelativePath = Joiner.on("/").join("plugins", pluginName);
    this.cfg = cfg;
    this.peerInfoProvider = peerInfoProvider;
  }

  @Override
  public boolean indexAccount(AccountIndexEvent event) {
    return execute(RequestMethod.POST, "index account", "index/account", event.accountId, event);
  }

  @Override
  public boolean indexChange(ChangeIndexEvent event) {
    return execute(
        RequestMethod.POST,
        "index change",
        "index/change",
        Url.encode(event.projectName) + "~" + event.changeId,
        event);
  }

  @Override
  public boolean deleteChangeFromIndex(ChangeIndexEvent event) {
    return execute(
        RequestMethod.DELETE, "delete change", "index/change", "~" + event.changeId, event);
  }

  @Override
  public boolean indexGroup(GroupIndexEvent event) {
    return execute(RequestMethod.POST, "index group", "index/group", event.groupUUID, event);
  }

  @Override
  public boolean indexProject(ProjectIndexEvent event) {
    return execute(
        RequestMethod.POST, "index project", "index/project", Url.encode(event.projectName), event);
  }

  @Override
  public boolean send(final Event event) {
    return execute(RequestMethod.POST, "send event", "event", event.type, event);
  }

  @Override
  public boolean evict(CacheEvictionEvent cacheEvictionEvent) {
    String json = GsonParser.toJson(cacheEvictionEvent.cacheName, cacheEvictionEvent.key);
    return execute(
        RequestMethod.POST,
        "invalidate cache " + cacheEvictionEvent.cacheName,
        "cache",
        cacheEvictionEvent.cacheName,
        json);
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

  private boolean execute(RequestMethod method, String action, String endpoint, Object id) {
    return execute(method, action, endpoint, id, null);
  }

  private boolean execute(
      RequestMethod method, String action, String endpoint, Object id, Object payload) {
    List<CompletableFuture<Boolean>> futures =
        peerInfoProvider
            .get()
            .stream()
            .map(peer -> createRequest(method, peer, action, endpoint, id, payload))
            .map(request -> CompletableFuture.supplyAsync(() -> request.execute()))
            .collect(Collectors.toList());
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    return futures.stream().allMatch(CompletableFuture::join);
  }

  private Request createRequest(
      RequestMethod method,
      PeerInfo peer,
      String action,
      String endpoint,
      Object id,
      Object payload) {
    String destination = peer.getDirectUrl();
    return new Request(action, id, destination) {
      @Override
      HttpResult send() throws IOException {
        String request = Joiner.on("/").join(destination, pluginRelativePath, endpoint, id);
        if (RequestMethod.POST == method) {
          return httpSession.post(request, payload);
        }
        return httpSession.delete(request, payload);
      }
    };
  }

  private abstract class Request {
    private final String action;
    private final Object key;
    private final String destination;

    private int execCnt;

    Request(String action, Object key, String destination) {
      this.action = action;
      this.key = key;
      this.destination = destination;
    }

    boolean execute() {
      log.debug("Executing {} {} towards {}", action, key, destination);
      for (; ; ) {
        try {
          execCnt++;
          tryOnce();
          log.debug("{} {} towards {} OK", action, key, destination);
          return true;
        } catch (ForwardingException e) {
          int maxTries = cfg.http().maxTries();
          log.debug(
              "Failed to {} {} on {} [{}/{}]", action, key, destination, execCnt, maxTries, e);
          if (!e.isRecoverable()) {
            log.error(
                "{} {} towards {} failed with unrecoverable error; giving up",
                action,
                key,
                destination,
                e);
            return false;
          }
          if (execCnt >= maxTries) {
            log.error(
                "Failed to {} {} on {} after {} tries; giving up",
                action,
                key,
                destination,
                maxTries);
            return false;
          }

          log.debug("Retrying to {} {} on {}", action, key, destination);
          try {
            Thread.sleep(cfg.http().retryInterval());
          } catch (InterruptedException ie) {
            log.error("{} {} towards {} was interrupted; giving up", action, key, destination, ie);
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
      Throwable cause = e.getCause();
      return !(e instanceof SSLException
          || cause instanceof HttpException
          || cause instanceof ClientProtocolException);
    }
  }
}
