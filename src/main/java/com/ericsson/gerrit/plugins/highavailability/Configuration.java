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

package com.ericsson.gerrit.plugins.highavailability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class Configuration {
  private static final Logger log = LoggerFactory.getLogger(Configuration.class);

  private static final int DEFAULT_TIMEOUT_MS = 5000;
  private static final int DEFAULT_MAX_TRIES = 5;
  private static final int DEFAULT_RETRY_INTERVAL = 1000;
  private static final int DEFAULT_THREAD_POOL_SIZE = 1;

  private final String url;
  private final String user;
  private final String password;
  private final int connectionTimeout;
  private final int socketTimeout;
  private final int maxTries;
  private final int retryInterval;
  private final int indexThreadPoolSize;
  private final int eventThreadPoolSize;

  @Inject
  Configuration(PluginConfigFactory config,
      @PluginName String pluginName) {
    PluginConfig cfg = config.getFromGerritConfig(pluginName, true);
    url = Strings.nullToEmpty(cfg.getString("url"));
    user = Strings.nullToEmpty(cfg.getString("user"));
    password = Strings.nullToEmpty(cfg.getString("password"));
    connectionTimeout = getInt(cfg, "connectionTimeout", DEFAULT_TIMEOUT_MS);
    socketTimeout = getInt(cfg, "socketTimeout", DEFAULT_TIMEOUT_MS);
    maxTries = getInt(cfg, "maxTries", DEFAULT_MAX_TRIES);
    retryInterval = getInt(cfg, "retryInterval", DEFAULT_RETRY_INTERVAL);
    indexThreadPoolSize =
        getInt(cfg, "indexThreadPoolSize", DEFAULT_THREAD_POOL_SIZE);
    eventThreadPoolSize =
        getInt(cfg, "eventThreadPoolSize", DEFAULT_THREAD_POOL_SIZE);
  }

  private int getInt(PluginConfig cfg, String name, int defaultValue) {
    try {
      return cfg.getInt(name, defaultValue);
    } catch (IllegalArgumentException e) {
      log.error(String.format(
          "invalid value for %s; using default value %d", name, defaultValue));
      return defaultValue;
    }
  }

  public int getConnectionTimeout() {
    return connectionTimeout;
  }

  public int getMaxTries() {
    return maxTries;
  }

  public int getRetryInterval() {
    return retryInterval;
  }

  public int getSocketTimeout() {
    return socketTimeout;
  }

  public String getUrl() {
    return CharMatcher.is('/').trimTrailingFrom(url);
  }

  public String getUser() {
    return user;
  }

  public String getPassword() {
    return password;
  }

  public int getIndexThreadPoolSize() {
    return indexThreadPoolSize;
  }

  public int getEventThreadPoolSize() {
    return eventThreadPoolSize;
  }
}
