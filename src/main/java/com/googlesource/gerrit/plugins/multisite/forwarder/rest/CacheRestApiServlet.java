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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.google.common.base.Splitter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheEntry;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheNotFoundException;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedCacheEvictionHandler;

import java.io.IOException;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
class CacheRestApiServlet extends AbstractRestApiServlet {
  private static final int CACHENAME_INDEX = 1;
  private static final long serialVersionUID = -1L;

  private final ForwardedCacheEvictionHandler forwardedCacheEvictionHandler;

  @Inject
  CacheRestApiServlet(ForwardedCacheEvictionHandler forwardedCacheEvictionHandler) {
    this.forwardedCacheEvictionHandler = forwardedCacheEvictionHandler;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp) {
    setHeaders(rsp);
    try {
      List<String> params = Splitter.on('/').splitToList(req.getPathInfo());
      String cacheName = params.get(CACHENAME_INDEX);
      String json = req.getReader().readLine();
      forwardedCacheEvictionHandler.evict(
          CacheEntry.from(cacheName, GsonParser.fromJson(cacheName, json)));
      rsp.setStatus(SC_NO_CONTENT);
    } catch (CacheNotFoundException e) {
      log.error("Failed to process eviction request: {}", e.getMessage());
      sendError(rsp, SC_BAD_REQUEST, e.getMessage());
    } catch (IOException e) {
      log.error("Failed to process eviction request: {}", e.getMessage(), e);
      sendError(rsp, SC_BAD_REQUEST, e.getMessage());
    }
  }
}
