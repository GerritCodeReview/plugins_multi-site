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

package com.googlesource.gerrit.plugins.multisite;

import com.google.common.base.CaseFormat;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Configuration {
  private static final Logger log = LoggerFactory.getLogger(Configuration.class);

  static final String INSTANCE_ID_FILE = "instanceId.data";
  // common parameter to peerInfo section
  static final String PEER_INFO_SECTION = "peerInfo";

  // common parameters to cache and index sections
  static final String THREAD_POOL_SIZE_KEY = "threadPoolSize";

  static final int DEFAULT_INDEX_MAX_TRIES = 2;
  static final int DEFAULT_INDEX_RETRY_INTERVAL = 30000;
  static final int DEFAULT_THREAD_POOL_SIZE = 4;
  static final String NUM_STRIPED_LOCKS = "numStripedLocks";
  static final int DEFAULT_NUM_STRIPED_LOCKS = 10;
  static final String ENABLE_KEY = "enable";

  private final Main main;
  private final AutoReindex autoReindex;
  private final PeerInfo peerInfo;
  private final Broker broker;
  private final Http http;
  private final Cache cache;
  private final Event event;
  private final Index index;
  private PeerInfoStatic peerInfoStatic;
  private HealthCheck healthCheck;

  public enum PeerInfoStrategy {
    STATIC
  }

  @Inject
  Configuration(
      PluginConfigFactory pluginConfigFactory, @PluginName String pluginName, SitePaths site) {
    Config cfg = pluginConfigFactory.getGlobalPluginConfig(pluginName);
    main = new Main(site, cfg);
    autoReindex = new AutoReindex(cfg);
    peerInfo = new PeerInfo(cfg);
    switch (peerInfo.strategy()) {
      case STATIC:
        peerInfoStatic = new PeerInfoStatic(cfg);
        break;
      default:
        throw new IllegalArgumentException("Not supported strategy: " + peerInfo.strategy);
    }
    broker = new Broker(cfg);
    http = new Http(cfg);
    cache = new Cache(cfg);
    event = new Event(cfg);
    index = new Index(cfg);
    healthCheck = new HealthCheck(cfg);
  }

  public Broker broker() {
    return broker;
  }

  public Main main() {
    return main;
  }

  public AutoReindex autoReindex() {
    return autoReindex;
  }

  public PeerInfo peerInfo() {
    return peerInfo;
  }

  public PeerInfoStatic peerInfoStatic() {
    return peerInfoStatic;
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

  public HealthCheck healthCheck() {
    return healthCheck;
  }

  private static int getInt(Config cfg, String section, String name, int defaultValue) {
    try {
      return cfg.getInt(section, name, defaultValue);
    } catch (IllegalArgumentException e) {
      log.error("invalid value for {}; using default value {}", name, defaultValue);
      log.debug("Failed to retrieve integer value: {}", e.getMessage(), e);
      return defaultValue;
    }
  }

  private static String getString(
      Config cfg, String section, String subsection, String name, String defaultValue) {
    String value = cfg.getString(section, subsection, name);
    if (!Strings.isNullOrEmpty(value)) {
      return value;
    }
    return defaultValue;
  }

  public static class Main {
    public static final String MAIN_SECTION = "main";
    public static final String SHARED_DIRECTORY_KEY = "sharedDirectory";
    public static final String DEFAULT_SHARED_DIRECTORY = "shared";

    private final Path sharedDirectory;

    private Main(SitePaths site, Config cfg) {
      String shared = Strings.emptyToNull(cfg.getString(MAIN_SECTION, null, SHARED_DIRECTORY_KEY));
      if (shared == null) {
        shared = DEFAULT_SHARED_DIRECTORY;
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

  public static class AutoReindex {
    static final String AUTO_REINDEX_SECTION = "autoReindex";
    static final String ENABLED = "enabled";
    static final String DELAY = "delay";
    static final String POLL_INTERVAL = "pollInterval";

    private final boolean enabled;
    private final long delaySec;
    private final long pollSec;

    public AutoReindex(Config cfg) {
      this.enabled = cfg.getBoolean(AUTO_REINDEX_SECTION, ENABLED, false);
      this.delaySec =
          ConfigUtil.getTimeUnit(cfg, AUTO_REINDEX_SECTION, null, DELAY, 10L, TimeUnit.SECONDS);
      this.pollSec =
          ConfigUtil.getTimeUnit(
              cfg, AUTO_REINDEX_SECTION, null, POLL_INTERVAL, 0L, TimeUnit.SECONDS);
    }

    public boolean enabled() {
      return enabled;
    }

    public long delaySec() {
      return delaySec;
    }

    public long pollSec() {
      return pollSec;
    }
  }

  public static class PeerInfo {
    static final PeerInfoStrategy DEFAULT_PEER_INFO_STRATEGY = PeerInfoStrategy.STATIC;
    static final String STRATEGY_KEY = "strategy";

    private final PeerInfoStrategy strategy;

    private PeerInfo(Config cfg) {
      strategy = cfg.getEnum(PEER_INFO_SECTION, null, STRATEGY_KEY, DEFAULT_PEER_INFO_STRATEGY);
      if (log.isDebugEnabled()) {
        log.debug("Strategy: {}", strategy.name());
      }
    }

    public PeerInfoStrategy strategy() {
      return strategy;
    }
  }

  public static class PeerInfoStatic {
    public static final String STATIC_SUBSECTION = PeerInfoStrategy.STATIC.name().toLowerCase();
    public static final String URL_KEY = "url";

    private final Set<String> urls;

    private PeerInfoStatic(Config cfg) {
      urls =
          Arrays.stream(cfg.getStringList(PEER_INFO_SECTION, STATIC_SUBSECTION, URL_KEY))
              .filter(Objects::nonNull)
              .filter(s -> !s.isEmpty())
              .map(s -> CharMatcher.is('/').trimTrailingFrom(s))
              .collect(Collectors.toSet());
      log.debug("Urls: {}", urls);
    }

    public Set<String> urls() {
      return ImmutableSet.copyOf(urls);
    }
  }

  public static class Broker extends Properties {
    private static final long serialVersionUID = 0L;

    public static final String KAFKA_STRING_SERIALIZER = StringSerializer.class.getName();

    static final String BROKER_SECTION = "broker";
    static final String BROKER_PRODUCER_INDEX_TOPIC = "index-event-topic";
    static final boolean DEFAULT_BROKER_ENABLED = false;

    private final boolean enabled;
    private final String indexEventTopic;

    private Broker(Config cfg) {
      enabled = cfg.getBoolean(BROKER_SECTION, ENABLE_KEY, DEFAULT_BROKER_ENABLED);
      indexEventTopic =
          getString(cfg, BROKER_SECTION, null, BROKER_PRODUCER_INDEX_TOPIC, "GERRIT.EVENT.INDEX");
      if (enabled) {
        setDefaults();
        applyConfig(cfg);
      }
    }

    private void setDefaults() {
      put("acks", "all");
      put("retries", 0);
      put("batch.size", 16384);
      put("linger.ms", 1);
      put("buffer.memory", 33554432);
      put("key.serializer", KAFKA_STRING_SERIALIZER);
      put("value.serializer", KAFKA_STRING_SERIALIZER);
      put("reconnect.backoff.ms", 5000L);
    }

    private void applyConfig(Config config) {
      for (String section : config.getSections()) {
        if (section.equals(BROKER_SECTION)) {
          for (String name : config.getNames(section, true)) {
            Object value = config.getString(BROKER_SECTION, null, name);
            String propName =
                CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, name).replaceAll("-", ".");
            put(propName, value);
          }
        }
      }
    }

    public boolean enabled() {
      return enabled;
    }

    public String getIndexEventTopic() {
      return indexEventTopic;
    }
  }

  public static class Http {
    static final String HTTP_SECTION = "http";
    static final String USER_KEY = "user";
    static final String PASSWORD_KEY = "password";
    static final String CONNECTION_TIMEOUT_KEY = "connectionTimeout";
    static final String SOCKET_TIMEOUT_KEY = "socketTimeout";
    static final String MAX_TRIES_KEY = "maxTries";
    static final String RETRY_INTERVAL_KEY = "retryInterval";
    static final boolean DEFAULT_HTTP_ENABLED = true;

    static final int DEFAULT_TIMEOUT_MS = 5000;
    static final int DEFAULT_MAX_TRIES = 360;
    static final int DEFAULT_RETRY_INTERVAL = 10000;

    private final boolean enabled;
    private final String user;
    private final String password;
    private final int connectionTimeout;
    private final int socketTimeout;
    private final int maxTries;
    private final int retryInterval;

    private Http(Config cfg) {
      enabled = cfg.getBoolean(HTTP_SECTION, ENABLE_KEY, DEFAULT_HTTP_ENABLED);
      user = Strings.nullToEmpty(cfg.getString(HTTP_SECTION, null, USER_KEY));
      password = Strings.nullToEmpty(cfg.getString(HTTP_SECTION, null, PASSWORD_KEY));
      connectionTimeout = getInt(cfg, HTTP_SECTION, CONNECTION_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS);
      socketTimeout = getInt(cfg, HTTP_SECTION, SOCKET_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS);
      maxTries = getInt(cfg, HTTP_SECTION, MAX_TRIES_KEY, DEFAULT_MAX_TRIES);
      retryInterval = getInt(cfg, HTTP_SECTION, RETRY_INTERVAL_KEY, DEFAULT_RETRY_INTERVAL);
    }

    public boolean enabled() {
      return enabled;
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

  /** Common parameters to cache, event, index */
  public abstract static class Forwarding {
    static final boolean DEFAULT_SYNCHRONIZE = true;
    static final String SYNCHRONIZE_KEY = "synchronize";

    private final boolean synchronize;

    private Forwarding(Config cfg, String section) {
      synchronize = getBoolean(cfg, section, SYNCHRONIZE_KEY, DEFAULT_SYNCHRONIZE);
    }

    private static boolean getBoolean(
        Config cfg, String section, String name, boolean defaultValue) {
      try {
        return cfg.getBoolean(section, name, defaultValue);
      } catch (IllegalArgumentException e) {
        log.error("invalid value for {}; using default value {}", name, defaultValue);
        log.debug("Failed to retrieve boolean value: {}", e.getMessage(), e);
        return defaultValue;
      }
    }

    public boolean synchronize() {
      return synchronize;
    }
  }

  public static class Cache extends Forwarding {
    static final String CACHE_SECTION = "cache";
    static final String PATTERN_KEY = "pattern";

    private final int threadPoolSize;
    private final List<String> patterns;

    private Cache(Config cfg) {
      super(cfg, CACHE_SECTION);
      threadPoolSize = getInt(cfg, CACHE_SECTION, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
      patterns = Arrays.asList(cfg.getStringList(CACHE_SECTION, null, PATTERN_KEY));
    }

    public int threadPoolSize() {
      return threadPoolSize;
    }

    public List<String> patterns() {
      return Collections.unmodifiableList(patterns);
    }
  }

  public static class Event extends Forwarding {
    static final String EVENT_SECTION = "event";

    private Event(Config cfg) {
      super(cfg, EVENT_SECTION);
    }
  }

  public static class Index extends Forwarding {
    static final String INDEX_SECTION = "index";
    static final String MAX_TRIES_KEY = "maxTries";
    static final String RETRY_INTERVAL_KEY = "retryInterval";

    private final int threadPoolSize;
    private final int retryInterval;
    private final int maxTries;

    private final int numStripedLocks;

    private Index(Config cfg) {
      super(cfg, INDEX_SECTION);
      threadPoolSize = getInt(cfg, INDEX_SECTION, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
      retryInterval = getInt(cfg, INDEX_SECTION, RETRY_INTERVAL_KEY, DEFAULT_INDEX_RETRY_INTERVAL);
      maxTries = getInt(cfg, INDEX_SECTION, MAX_TRIES_KEY, DEFAULT_INDEX_MAX_TRIES);
      numStripedLocks = getInt(cfg, INDEX_SECTION, NUM_STRIPED_LOCKS, DEFAULT_NUM_STRIPED_LOCKS);
    }

    public int threadPoolSize() {
      return threadPoolSize;
    }

    public int retryInterval() {
      return retryInterval;
    }

    public int maxTries() {
      return maxTries;
    }

    public int numStripedLocks() {
      return numStripedLocks;
    }
  }

  public static class HealthCheck {
    static final String HEALTH_CHECK_SECTION = "healthCheck";
    static final boolean DEFAULT_HEALTH_CHECK_ENABLED = true;

    private final boolean enabled;

    private HealthCheck(Config cfg) {
      enabled = cfg.getBoolean(HEALTH_CHECK_SECTION, ENABLE_KEY, DEFAULT_HEALTH_CHECK_ENABLED);
    }

    public boolean enabled() {
      return enabled;
    }
  }
}
