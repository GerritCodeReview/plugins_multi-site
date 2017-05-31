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

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Configuration {
  private static final Logger log = LoggerFactory.getLogger(Configuration.class);

  static final String SHARED_DIRECTORY_KEY = "sharedDirectory";
  static final String URL_KEY = "url";
  static final String USER_KEY = "user";
  static final String PASSWORD_KEY = "password";
  static final String CONNECTION_TIMEOUT_KEY = "connectionTimeout";
  static final String SOCKET_TIMEOUT_KEY = "socketTimeout";
  static final String MAX_TRIES_KEY = "maxTries";
  static final String RETRY_INTERVAL_KEY = "retryInterval";
  static final String INDEX_THREAD_POOL_SIZE_KEY = "indexThreadPoolSize";
  static final String CACHE_THREAD_POOL_SIZE_KEY = "cacheThreadPoolSize";

  static final int DEFAULT_TIMEOUT_MS = 5000;
  static final int DEFAULT_MAX_TRIES = 5;
  static final int DEFAULT_RETRY_INTERVAL = 1000;
  static final int DEFAULT_THREAD_POOL_SIZE = 1;

  private final String url;
  private final String user;
  private final String password;
  private final int connectionTimeout;
  private final int socketTimeout;
  private final int maxTries;
  private final int retryInterval;
  private final int indexThreadPoolSize;
  private final int cacheThreadPoolSize;
  private final String sharedDirectory;

  @Inject
  Configuration(PluginConfigFactory config, @PluginName String pluginName) {
    PluginConfig cfg = config.getFromGerritConfig(pluginName, true);
    url = Strings.nullToEmpty(cfg.getString(URL_KEY));
    user = Strings.nullToEmpty(cfg.getString(USER_KEY));
    password = Strings.nullToEmpty(cfg.getString(PASSWORD_KEY));
    connectionTimeout = getInt(cfg, CONNECTION_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS);
    socketTimeout = getInt(cfg, SOCKET_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS);
    maxTries = getInt(cfg, MAX_TRIES_KEY, DEFAULT_MAX_TRIES);
    retryInterval = getInt(cfg, RETRY_INTERVAL_KEY, DEFAULT_RETRY_INTERVAL);
    indexThreadPoolSize = getInt(cfg, INDEX_THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
    cacheThreadPoolSize = getInt(cfg, CACHE_THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
    sharedDirectory = Strings.emptyToNull(cfg.getString(SHARED_DIRECTORY_KEY));
    if (sharedDirectory == null) {
      throw new ProvisionException(SHARED_DIRECTORY_KEY + " must be configured");
    }
  }

  private int getInt(PluginConfig cfg, String name, int defaultValue) {
    try {
      return cfg.getInt(name, defaultValue);
    } catch (IllegalArgumentException e) {
      log.error(String.format("invalid value for %s; using default value %d", name, defaultValue));
      log.debug("Failed retrieve integer value: " + e.getMessage(), e);
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

  public int getCacheThreadPoolSize() {
    return cacheThreadPoolSize;
  }

  public String getSharedDirectory() {
    return sharedDirectory;
  }
}
