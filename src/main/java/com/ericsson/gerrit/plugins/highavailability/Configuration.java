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

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Configuration {
  private static final Logger log = LoggerFactory.getLogger(Configuration.class);

  // main section
  static final String MAIN_SECTION = "main";
  static final String SHARED_DIRECTORY_KEY = "sharedDirectory";
  static final String DEFAULT_SHARED_DIRECTORY = "shared";

  // peerInfo section
  static final String PEER_INFO_SECTION = "peerInfo";
  static final String STATIC_SUBSECTION = PeerInfoStrategy.STATIC.name().toLowerCase();
  static final String JGROUPS_SUBSECTION = PeerInfoStrategy.JGROUPS.name().toLowerCase();
  static final String URL_KEY = "url";
  static final String STRATEGY_KEY = "strategy";

  // http section
  static final String HTTP_SECTION = "http";
  static final String USER_KEY = "user";
  static final String PASSWORD_KEY = "password";
  static final String CONNECTION_TIMEOUT_KEY = "connectionTimeout";
  static final String SOCKET_TIMEOUT_KEY = "socketTimeout";
  static final String MAX_TRIES_KEY = "maxTries";
  static final String RETRY_INTERVAL_KEY = "retryInterval";

  // cache section
  static final String CACHE_SECTION = "cache";

  // event section
  static final String EVENT_SECTION = "event";

  // index section
  static final String INDEX_SECTION = "index";

  // common parameters to cache and index sections
  static final String THREAD_POOL_SIZE_KEY = "threadPoolSize";

  // common parameters to cache, event index and websession sections
  static final String SYNCHRONIZE_KEY = "synchronize";

  // websession section
  static final String WEBSESSION_SECTION = "websession";
  static final String CLEANUP_INTERVAL_KEY = "cleanupInterval";

  // jgroups section used if peerInfo.strategy == jgroups
  static final String SKIP_INTERFACE_KEY = "skipInterface";
  static final String CLUSTER_NAME_KEY = "clusterName";

  static final int DEFAULT_TIMEOUT_MS = 5000;
  static final int DEFAULT_MAX_TRIES = 5;
  static final int DEFAULT_RETRY_INTERVAL = 1000;
  static final int DEFAULT_THREAD_POOL_SIZE = 1;
  static final String DEFAULT_CLEANUP_INTERVAL = "24 hours";
  static final long DEFAULT_CLEANUP_INTERVAL_MS = HOURS.toMillis(24);
  static final boolean DEFAULT_SYNCHRONIZE = true;
  static final PeerInfoStrategy DEFAULT_PEER_INFO_STRATEGY = PeerInfoStrategy.STATIC;
  static final ImmutableList<String> DEFAULT_SKIP_INTERFACE_LIST =
      ImmutableList.of("lo*", "utun*", "awdl*");
  static final String DEFAULT_CLUSTER_NAME = "GerritHA";

  private final Main main;
  private final PeerInfo peerInfo;
  private final Http http;
  private final Cache cache;
  private final Event event;
  private final Index index;
  private final Websession websession;
  private PeerInfoStatic peerInfoStatic;
  private PeerInfoJGroups peerInfoJGroups;

  public enum PeerInfoStrategy {
    JGROUPS,
    STATIC
  }

  @Inject
  Configuration(
      PluginConfigFactory pluginConfigFactory, @PluginName String pluginName, SitePaths site) {
    Config cfg = pluginConfigFactory.getGlobalPluginConfig(pluginName);
    main = new Main(site, cfg);
    peerInfo = new PeerInfo(cfg);
    switch (peerInfo.strategy()) {
      case STATIC:
        peerInfoStatic = new PeerInfoStatic(cfg);
        break;
      case JGROUPS:
        peerInfoJGroups = new PeerInfoJGroups(cfg);
        break;
      default:
        throw new IllegalArgumentException("Not supported strategy: " + peerInfo.strategy);
    }
    http = new Http(cfg);
    cache = new Cache(cfg);
    event = new Event(cfg);
    index = new Index(cfg);
    websession = new Websession(cfg);
  }

  public Main main() {
    return main;
  }

  public PeerInfo peerInfo() {
    return peerInfo;
  }

  public PeerInfoStatic peerInfoStatic() {
    return peerInfoStatic;
  }

  public PeerInfoJGroups peerInfoJGroups() {
    return peerInfoJGroups;
  }

  public Http http() {
    return http;
  }

  public Cache cache() {
    return cache;
  }

  public Event event() {
    return event;
  }

  public Index index() {
    return index;
  }

  public Websession websession() {
    return websession;
  }

  private static int getInt(Config cfg, String section, String name, int defaultValue) {
    try {
      return cfg.getInt(section, name, defaultValue);
    } catch (IllegalArgumentException e) {
      log.error(String.format("invalid value for %s; using default value %d", name, defaultValue));
      log.debug("Failed to retrieve integer value: " + e.getMessage(), e);
      return defaultValue;
    }
  }

  private static boolean getBoolean(Config cfg, String section, String name, boolean defaultValue) {
    try {
      return cfg.getBoolean(section, name, defaultValue);
    } catch (IllegalArgumentException e) {
      log.error(String.format("invalid value for %s; using default value %s", name, defaultValue));
      log.debug("Failed to retrieve boolean value: " + e.getMessage(), e);
      return defaultValue;
    }
  }

  private static String getString(
      Config cfg, String section, String subSection, String name, String defaultValue) {
    String value = cfg.getString(section, subSection, name);
    return ((value == null) ? defaultValue : value);
  }

  public static class Main {
    private final Path sharedDirectory;

    private Main(SitePaths site, Config cfg) {
      String shared = Strings.emptyToNull(cfg.getString(MAIN_SECTION, null, SHARED_DIRECTORY_KEY));
      if (shared == null) {
        throw new ProvisionException(SHARED_DIRECTORY_KEY + " must be configured");
      }
      Path p = Paths.get(shared);
      if (p.isAbsolute()) {
        sharedDirectory = p;
      } else {
        sharedDirectory = site.resolve(shared);
      }
    }

    public Path sharedDirectory() {
      return sharedDirectory;
    }
  }

  public static class PeerInfo {
    private final PeerInfoStrategy strategy;

    private PeerInfo(Config cfg) {
      strategy = cfg.getEnum(PEER_INFO_SECTION, null, STRATEGY_KEY, DEFAULT_PEER_INFO_STRATEGY);
    }

    public PeerInfoStrategy strategy() {
      return strategy;
    }
  }

  public class PeerInfoStatic {
    private final String url;

    private PeerInfoStatic(Config cfg) {
      url =
          CharMatcher.is('/')
              .trimTrailingFrom(
                  Strings.nullToEmpty(
                      cfg.getString(PEER_INFO_SECTION, STATIC_SUBSECTION, URL_KEY)));
    }

    public String url() {
      return url;
    }
  }

  public static class PeerInfoJGroups {
    private final ImmutableList<String> skipInterface;
    private final String clusterName;

    private PeerInfoJGroups(Config cfg) {
      String[] skip = cfg.getStringList(PEER_INFO_SECTION, JGROUPS_SUBSECTION, SKIP_INTERFACE_KEY);
      skipInterface = skip == null ? DEFAULT_SKIP_INTERFACE_LIST : ImmutableList.copyOf(skip);
      clusterName =
          getString(
              cfg, PEER_INFO_SECTION, JGROUPS_SUBSECTION, CLUSTER_NAME_KEY, DEFAULT_CLUSTER_NAME);
    }

    public ImmutableList<String> skipInterface() {
      return skipInterface;
    }

    public String clusterName() {
      return clusterName;
    }
  }

  public static class Http {
    private final String user;
    private final String password;
    private final int connectionTimeout;
    private final int socketTimeout;
    private final int maxTries;
    private final int retryInterval;

    private Http(Config cfg) {
      user = Strings.nullToEmpty(cfg.getString(HTTP_SECTION, null, USER_KEY));
      password = Strings.nullToEmpty(cfg.getString(HTTP_SECTION, null, PASSWORD_KEY));
      connectionTimeout = getInt(cfg, HTTP_SECTION, CONNECTION_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS);
      socketTimeout = getInt(cfg, HTTP_SECTION, SOCKET_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS);
      maxTries = getInt(cfg, HTTP_SECTION, MAX_TRIES_KEY, DEFAULT_MAX_TRIES);
      retryInterval = getInt(cfg, HTTP_SECTION, RETRY_INTERVAL_KEY, DEFAULT_RETRY_INTERVAL);
    }

    public String user() {
      return user;
    }

    public String password() {
      return password;
    }

    public int connectionTimeout() {
      return connectionTimeout;
    }

    public int socketTimeout() {
      return socketTimeout;
    }

    public int maxTries() {
      return maxTries;
    }

    public int retryInterval() {
      return retryInterval;
    }
  }

  /** Common parameters to cache, event, index and websession */
  public abstract static class Forwarding {
    private final boolean synchronize;

    private Forwarding(Config cfg, String section) {
      synchronize = getBoolean(cfg, section, SYNCHRONIZE_KEY, DEFAULT_SYNCHRONIZE);
    }

    public boolean synchronize() {
      return synchronize;
    }
  }

  public static class Cache extends Forwarding {
    private final int threadPoolSize;

    private Cache(Config cfg) {
      super(cfg, CACHE_SECTION);
      threadPoolSize = getInt(cfg, CACHE_SECTION, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
    }

    public int threadPoolSize() {
      return threadPoolSize;
    }
  }

  public static class Event extends Forwarding {
    private Event(Config cfg) {
      super(cfg, EVENT_SECTION);
    }
  }

  public static class Index extends Forwarding {
    private final int threadPoolSize;

    private Index(Config cfg) {
      super(cfg, INDEX_SECTION);
      threadPoolSize = getInt(cfg, INDEX_SECTION, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
    }

    public int threadPoolSize() {
      return threadPoolSize;
    }
  }

  public static class Websession extends Forwarding {
    private final long cleanupInterval;

    private Websession(Config cfg) {
      super(cfg, WEBSESSION_SECTION);
      this.cleanupInterval =
          ConfigUtil.getTimeUnit(
              Strings.nullToEmpty(cfg.getString(WEBSESSION_SECTION, null, CLEANUP_INTERVAL_KEY)),
              DEFAULT_CLEANUP_INTERVAL_MS,
              MILLISECONDS);
    }

    public long cleanupInterval() {
      return cleanupInterval;
    }
  }
}
