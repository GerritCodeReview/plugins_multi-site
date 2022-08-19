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

import static com.google.common.base.Suppliers.memoize;
import static com.google.common.base.Suppliers.ofInstance;

import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDbConfiguration;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.spi.Message;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Configuration {
  private static final Logger log = LoggerFactory.getLogger(Configuration.class);

  public static final String PLUGIN_NAME = "multi-site";
  public static final String MULTI_SITE_CONFIG = PLUGIN_NAME + ".config";

  static final String INSTANCE_ID_FILE = "instanceId.data";
  static final String THREAD_POOL_SIZE_KEY = "threadPoolSize";
  static final int DEFAULT_THREAD_POOL_SIZE = 4;

  private static final String REF_DATABASE = "ref-database";
  private static final String REPLICATION_LAG_REFRESH_INTERVAL = "replicationLagRefreshInterval";
  private static final Duration REPLICATION_LAG_REFRESH_INTERVAL_DEFAULT = Duration.ofSeconds(60);

  private static final String REPLICATION_CONFIG = "replication.config";
  // common parameters to cache and index sections
  private static final int DEFAULT_INDEX_MAX_TRIES = 2;
  private static final int DEFAULT_INDEX_RETRY_INTERVAL = 30000;
  private static final String NUM_STRIPED_LOCKS = "numStripedLocks";
  private static final int DEFAULT_NUM_STRIPED_LOCKS = 10;

  private final Supplier<Cache> cache;
  private final Supplier<Event> event;
  private final Supplier<Index> index;
  private final Supplier<Projects> projects;
  private final Supplier<SharedRefDbConfiguration> sharedRefDb;
  private final Supplier<Collection<Message>> replicationConfigValidation;
  private final Supplier<Broker> broker;
  private final Config multiSiteConfig;
  private final Supplier<Duration> replicationLagRefreshInterval;

  @Inject
  Configuration(SitePaths sitePaths) {
    this(getConfigFile(sitePaths, MULTI_SITE_CONFIG), getConfigFile(sitePaths, REPLICATION_CONFIG));
  }

  @VisibleForTesting
  public Configuration(Config multiSiteConfig, Config replicationConfig) {
    Supplier<Config> lazyMultiSiteCfg = lazyLoad(multiSiteConfig);
    this.multiSiteConfig = multiSiteConfig;
    replicationConfigValidation = lazyValidateReplicatioConfig(replicationConfig);
    cache = memoize(() -> new Cache(lazyMultiSiteCfg));
    event = memoize(() -> new Event(lazyMultiSiteCfg));
    index = memoize(() -> new Index(lazyMultiSiteCfg));
    projects = memoize(() -> new Projects(lazyMultiSiteCfg));
    sharedRefDb =
        memoize(
            () ->
                new SharedRefDbConfiguration(
                    enableSharedRefDbByDefault(lazyMultiSiteCfg.get()), PLUGIN_NAME));
    broker = memoize(() -> new Broker(lazyMultiSiteCfg));
    replicationLagRefreshInterval =
        memoize(
            () ->
                Duration.ofMillis(
                    ConfigUtil.getTimeUnit(
                        lazyMultiSiteCfg.get(),
                        REF_DATABASE,
                        null,
                        REPLICATION_LAG_REFRESH_INTERVAL,
                        REPLICATION_LAG_REFRESH_INTERVAL_DEFAULT.toMillis(),
                        TimeUnit.MILLISECONDS)));
  }

  public Config getMultiSiteConfig() {
    return multiSiteConfig;
  }

  public SharedRefDbConfiguration getSharedRefDbConfiguration() {
    return sharedRefDb.get();
  }

  public Cache cache() {
    return cache.get();
  }

  public Event event() {
    return event.get();
  }

  public Index index() {
    return index.get();
  }

  public Broker broker() {
    return broker.get();
  }

  public Projects projects() {
    return projects.get();
  }

  public Duration replicationLagRefreshInterval() {
    return replicationLagRefreshInterval.get();
  }

  public Collection<Message> validate() {
    return replicationConfigValidation.get();
  }

  private static FileBasedConfig getConfigFile(SitePaths sitePaths, String configFileName) {
    return new FileBasedConfig(sitePaths.etc_dir.resolve(configFileName).toFile(), FS.DETECTED);
  }

  private Config enableSharedRefDbByDefault(Config cfg) {
    if (Strings.isNullOrEmpty(
        cfg.getString(
            SharedRefDbConfiguration.SharedRefDatabase.SECTION,
            null,
            SharedRefDbConfiguration.SharedRefDatabase.ENABLE_KEY))) {
      cfg.setBoolean(
          SharedRefDbConfiguration.SharedRefDatabase.SECTION,
          null,
          SharedRefDbConfiguration.SharedRefDatabase.ENABLE_KEY,
          true);
    }
    return cfg;
  }

  private Supplier<Config> lazyLoad(Config config) {
    if (config instanceof FileBasedConfig) {
      return memoize(
          () -> {
            FileBasedConfig fileConfig = (FileBasedConfig) config;
            String fileConfigFileName = fileConfig.getFile().getPath();
            try {
              log.info("Loading configuration from {}", fileConfigFileName);
              fileConfig.load();
            } catch (IOException | ConfigInvalidException e) {
              log.error("Unable to load configuration from " + fileConfigFileName, e);
            }
            return fileConfig;
          });
    }
    return ofInstance(config);
  }

  private Supplier<Collection<Message>> lazyValidateReplicatioConfig(Config replicationConfig) {
    if (replicationConfig instanceof FileBasedConfig) {
      FileBasedConfig fileConfig = (FileBasedConfig) replicationConfig;
      try {
        fileConfig.load();
        return memoize(() -> validateReplicationConfig(replicationConfig));
      } catch (IOException | ConfigInvalidException e) {
        return ofInstance(Arrays.asList(new Message("Unable to load replication.config", e)));
      }
    }
    return ofInstance(validateReplicationConfig(replicationConfig));
  }

  private Collection<Message> validateReplicationConfig(Config replicationConfig) {
    if (replicationConfig.getBoolean("gerrit", "replicateOnStartup", false)) {
      return Arrays.asList(
          new Message(
              "Invalid replication.config: gerrit.replicateOnStartup has to be set to 'false' for multi-site setups"));
    }
    return Collections.emptyList();
  }

  private static int getInt(
      Supplier<Config> cfg, String section, String subSection, String name, int defaultValue) {
    try {
      return cfg.get().getInt(section, subSection, name, defaultValue);
    } catch (IllegalArgumentException e) {
      log.error("invalid value for {}; using default value {}", name, defaultValue);
      log.debug("Failed to retrieve integer value: {}", e.getMessage(), e);
      return defaultValue;
    }
  }

  public static class Projects {
    public static final String SECTION = "projects";
    public static final String PATTERN_KEY = "pattern";
    public List<String> patterns;

    public Projects(Supplier<Config> cfg) {
      patterns = ImmutableList.copyOf(cfg.get().getStringList("projects", null, PATTERN_KEY));
    }

    public List<String> getPatterns() {
      return patterns;
    }
  }

  /** Common parameters to cache, event, index */
  public abstract static class Forwarding {
    static final boolean DEFAULT_SYNCHRONIZE = true;
    static final String SYNCHRONIZE_KEY = "synchronize";

    private final boolean synchronize;

    private Forwarding(Supplier<Config> cfg, String section) {
      synchronize =
          Configuration.getBoolean(cfg, section, null, SYNCHRONIZE_KEY, DEFAULT_SYNCHRONIZE);
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

    private Cache(Supplier<Config> cfg) {
      super(cfg, CACHE_SECTION);
      threadPoolSize =
          getInt(cfg, CACHE_SECTION, null, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
      patterns = Arrays.asList(cfg.get().getStringList(CACHE_SECTION, null, PATTERN_KEY));
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

    private Event(Supplier<Config> cfg) {
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

    private Index(Supplier<Config> cfg) {
      super(cfg, INDEX_SECTION);
      threadPoolSize =
          getInt(cfg, INDEX_SECTION, null, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
      retryInterval =
          getInt(cfg, INDEX_SECTION, null, RETRY_INTERVAL_KEY, DEFAULT_INDEX_RETRY_INTERVAL);
      maxTries = getInt(cfg, INDEX_SECTION, null, MAX_TRIES_KEY, DEFAULT_INDEX_MAX_TRIES);
      numStripedLocks =
          getInt(cfg, INDEX_SECTION, null, NUM_STRIPED_LOCKS, DEFAULT_NUM_STRIPED_LOCKS);
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

  public static class Broker {
    static final String BROKER_SECTION = "broker";
    private final Config cfg;

    Broker(Supplier<Config> cfgSupplier) {
      cfg = cfgSupplier.get();
    }

    public String getTopic(String topicKey, String defValue) {
      return MoreObjects.firstNonNull(cfg.getString(BROKER_SECTION, null, topicKey), defValue);
    }
  }

  static boolean getBoolean(
      Supplier<Config> cfg, String section, String subsection, String name, boolean defaultValue) {
    try {
      return cfg.get().getBoolean(section, subsection, name, defaultValue);
    } catch (IllegalArgumentException e) {
      log.error("invalid value for {}; using default value {}", name, defaultValue);
      log.debug("Failed to retrieve boolean value: {}", e.getMessage(), e);
      return defaultValue;
    }
  }
}
