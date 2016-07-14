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

package com.ericsson.gerrit.plugins.evictcache;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.google.common.base.Splitter;
import com.google.common.cache.Cache;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
class EvictCacheRestApiServlet extends HttpServlet {
  private static final int CACHENAME_INDEX = 1;
  private static final long serialVersionUID = -1L;
  private static final String SOURCE_NAME = "gerrit";
  private static final String PROJECT_LIST = "project_list";
  private static final Logger logger =
      LoggerFactory.getLogger(EvictCacheRestApiServlet.class);

  private final DynamicMap<Cache<?, ?>> cacheMap;

  @Inject
  EvictCacheRestApiServlet(DynamicMap<Cache<?, ?>> cacheMap) {
    this.cacheMap = cacheMap;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    rsp.setContentType("text/plain");
    rsp.setCharacterEncoding("UTF-8");
    try {
      List<String> params = Splitter.on('/').splitToList(req.getPathInfo());
      String cacheName = params.get(CACHENAME_INDEX);
      String json = req.getReader().readLine();
      Object key = GsonParser.fromJson(cacheName, json);
      Cache<?, ?> cache = cacheMap.get(SOURCE_NAME, cacheName);
      Context.setForwardedEvent();
      evictCache(cache, cacheName, key);
      rsp.setStatus(SC_NO_CONTENT);
    } catch (IOException e) {
      logger.error("Failed to process eviction request: " + e.getMessage(), e);
      sendError(rsp, SC_BAD_REQUEST, e.getMessage());
    } finally {
      Context.unsetForwardedEvent();
    }
  }

  private static void sendError(HttpServletResponse rsp, int statusCode,
      String message) {
    try {
      rsp.sendError(statusCode, message);
    } catch (IOException e) {
      logger.error("Failed to send error messsage: " + e.getMessage(), e);
    }
  }

  private void evictCache(Cache<?, ?> cache, String cacheName, Object key) {
    if (PROJECT_LIST.equals(cacheName)) {
      // One key is holding the list of projects
      cache.invalidateAll();
    } else {
      cache.invalidate(key);
    }
    logger.debug("Invalidated " + cacheName);
  }
}
