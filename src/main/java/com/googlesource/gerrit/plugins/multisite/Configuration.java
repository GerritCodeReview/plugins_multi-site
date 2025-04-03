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
import java.util.Random;
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
  private static final String REPLICATION_LAG_ENABLED = "replicationLagEnabled";
  private static final Duration REPLICATION_LAG_REFRESH_INTERVAL_DEFAULT = Duration.ofSeconds(60);
  private static final String PUSH_REPLICATION_FILTER_ENABLED = "pushReplicationFilterEnabled";
  private static final String PULL_REPLICATION_FILTER_ENABLED = "pullReplicationFilterEnabled";
  private static final String LOCAL_REF_LOCK_TIMEOUT = "localRefLockTimeout";
  private static final Duration LOCAL_REF_LOCK_TIMEOUT_DEFAULT = Duration.ofSeconds(30);

  private static final String REPLICATION_CONFIG = "replication.config";
  // common parameters to cache and index sections
  private static final int DEFAULT_INDEX_MAX_TRIES = 2;
  private static final int DEFAULT_INDEX_RETRY_INTERVAL = 30000;
  private static final String NUM_STRIPED_LOCKS = "numStripedLocks";
  private static final int DEFAULT_NUM_STRIPED_LOCKS = 10;

  private static final long DEFALULT_STREAM_EVENT_PUBLISH_TIMEOUT = 30000;

  private final Supplier<Cache> cache;
  private final Supplier<Event> event;
  private final Supplier<Index> index;
  private final Supplier<Projects> projects;
  private final Supplier<SharedRefDbConfiguration> sharedRefDb;
  private final Supplier<Collection<Message>> replicationConfigValidation;
  private final Supplier<Broker> broker;
  private final Supplier<ReplicationFilter> replicationFilter;
  private final Config multiSiteConfig;
  private final Supplier<Duration> replicationLagRefreshInterval;
  private final Supplier<Boolean> replicationLagEnabled;
  private final Supplier<Boolean> pushReplicationFilterEnabled;
  private final Supplier<Boolean> pullReplicationFilterEnabled;
  private final Supplier<Long> localRefLockTimeoutMsec;

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
    replicationFilter = memoize(() -> new ReplicationFilter(lazyMultiSiteCfg));
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

    pushReplicationFilterEnabled =
        memoize(
            () ->
                lazyMultiSiteCfg
                    .get()
                    .getBoolean(REF_DATABASE, null, PUSH_REPLICATION_FILTER_ENABLED, true));
    pullReplicationFilterEnabled =
        memoize(
            () ->
                lazyMultiSiteCfg
                    .get()
                    .getBoolean(REF_DATABASE, null, PULL_REPLICATION_FILTER_ENABLED, true));


    replicationLagEnabled =
        memoize(
            () -> {
              if (pullReplicationFilterEnabled.get()) {
                return false;
              } else {
                return lazyMultiSiteCfg
                    .get()
                    .getBoolean(REF_DATABASE, null, REPLICATION_LAG_ENABLED, true);
              }
            });


    localRefLockTimeoutMsec =
        memoize(
            () ->
                ConfigUtil.getTimeUnit(
                    lazyMultiSiteCfg.get(),
                    REF_DATABASE,
                    null,
                    LOCAL_REF_LOCK_TIMEOUT,
                    LOCAL_REF_LOCK_TIMEOUT_DEFAULT.toMillis(),
                    TimeUnit.MILLISECONDS));
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

  public ReplicationFilter replicationFilter() {
    return replicationFilter.get();
  }

  public Duration replicationLagRefreshInterval() {
    return replicationLagRefreshInterval.get();
  }

  public boolean replicationLagEnabled() {
    return replicationLagEnabled.get();
  }

  public boolean pushReplicationFilterEnabled() {
    return pushReplicationFilterEnabled.get();
  }

  public boolean pullRepllicationFilterEnabled() {
    return pullReplicationFilterEnabled.get();
  }

  public long localRefLockTimeoutMsec() {
    return localRefLockTimeoutMsec.get();
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
      if (cfg instanceof FileBasedConfig) {
        try {
          ((FileBasedConfig) cfg).save();
        } catch (IOException e) {
          throw new IllegalStateException("Error while enabling global-refdb by default", e);
        }
      }
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
              "Invalid replication.config: gerrit.replicateOnStartup has to be set to 'false' for"
                  + " multi-site setups"));
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

  private static long getLong(
      Supplier<Config> cfg, String section, String subSection, String name, long defaultValue) {
    try {
      return cfg.get().getLong(section, subSection, name, defaultValue);
    } catch (IllegalArgumentException e) {
      log.error("invalid value for {}; using default value {}", name, defaultValue);
      log.debug("Failed to retrieve long value: {}", e.getMessage(), e);
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

  /**
   * Common parameters to cache, event, index
   */
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
    static final String SYNCHRONIZE_FORCED_KEY = "synchronizeForced";
    static final boolean DEFAULT_SYNCHRONIZE_FORCED = true;

    private final int threadPoolSize;
    private final int retryInterval;
    private final int maxTries;

    private final int numStripedLocks;
    private final boolean synchronizeForced;

    private Index(Supplier<Config> cfg) {
      super(cfg, INDEX_SECTION);
      threadPoolSize =
          getInt(cfg, INDEX_SECTION, null, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
      retryInterval =
          getInt(cfg, INDEX_SECTION, null, RETRY_INTERVAL_KEY, DEFAULT_INDEX_RETRY_INTERVAL);
      maxTries = getInt(cfg, INDEX_SECTION, null, MAX_TRIES_KEY, DEFAULT_INDEX_MAX_TRIES);
      numStripedLocks =
          getInt(cfg, INDEX_SECTION, null, NUM_STRIPED_LOCKS, DEFAULT_NUM_STRIPED_LOCKS);
      synchronizeForced =
          getBoolean(cfg, INDEX_SECTION, null, SYNCHRONIZE_FORCED_KEY, DEFAULT_SYNCHRONIZE_FORCED);
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

    public boolean synchronizeForced() {
      return synchronizeForced;
    }
  }

  public static class Broker {
    static final String BROKER_SECTION = "broker";
    static final String STREAM_EVENT_PUBLISH_TIMEOUT = "streamEventPublishTimeoutMs";
    private final Config cfg;
    private long streamEventPublishTimeout;

    Broker(Supplier<Config> cfgSupplier) {
      cfg = cfgSupplier.get();
      streamEventPublishTimeout =
          getLong(
              cfgSupplier,
              BROKER_SECTION,
              null,
              STREAM_EVENT_PUBLISH_TIMEOUT,
              DEFALULT_STREAM_EVENT_PUBLISH_TIMEOUT);
    }

    public String getTopic(String topicKey, String defValue) {
      return MoreObjects.firstNonNull(cfg.getString(BROKER_SECTION, null, topicKey), defValue);
    }

    public long getStreamEventPublishTimeout() {
      return streamEventPublishTimeout;
    }
  }

  public static class ReplicationFilter {
    static final String REPLICATION_FILTER_SECTION = "replication";
    static final String REPLICATION_PUSH_FILTER_SUBSECTION = "push-filter";
    static final String REPLICATION_FETCH_FILTER_SUBSECTION = "fetch-filter";

    private static final String MIN_WAIT_BEFORE_RELOAD_LOCAL_VERSION_MS =
        "minWaitBeforeReloadLocalVersionMs";
    private static final String RANDOM_WAIT_MAX_BOUND_BEFORE_RELOAD_LOCAL_VERSION_MS =
        "maxRandomWaitBeforeReloadLocalVersionMs";

    private static final int MIN_WAIT_BEFORE_RELOAD_LOCAL_VERSION_MS_DEFAULT = 1000;
    private static final int RANDOM_WAIT_MAX_BOUND_BEFORE_RELOAD_LOCAL_VERSION_MS_DEFAULT = 1000;
    private final Supplier<Integer> fetchMinWaitBeforeReloadLocalVersionMs;
    private final Supplier<Integer> fetchWaitBeforeReloadLocalVersionMs;
    private final Supplier<Integer> pushMinWaitBeforeReloadLocalVersionMs;
    private final Supplier<Integer> pushWaitBeforeReloadLocalVersionMs;

    public ReplicationFilter(Supplier<Config> cfg) {
      fetchMinWaitBeforeReloadLocalVersionMs =
          memoize(
              () ->
                  cfg.get()
                      .getInt(
                          REPLICATION_FILTER_SECTION,
                          REPLICATION_FETCH_FILTER_SUBSECTION,
                          MIN_WAIT_BEFORE_RELOAD_LOCAL_VERSION_MS,
                          MIN_WAIT_BEFORE_RELOAD_LOCAL_VERSION_MS_DEFAULT));
      fetchWaitBeforeReloadLocalVersionMs =
          memoize(
              () ->
                  cfg.get()
                      .getInt(
                          REPLICATION_FILTER_SECTION,
                          REPLICATION_FETCH_FILTER_SUBSECTION,
                          RANDOM_WAIT_MAX_BOUND_BEFORE_RELOAD_LOCAL_VERSION_MS,
                          RANDOM_WAIT_MAX_BOUND_BEFORE_RELOAD_LOCAL_VERSION_MS_DEFAULT));
      pushMinWaitBeforeReloadLocalVersionMs =
          memoize(
              () ->
                  cfg.get()
                      .getInt(
                          REPLICATION_FILTER_SECTION,
                          REPLICATION_PUSH_FILTER_SUBSECTION,
                          MIN_WAIT_BEFORE_RELOAD_LOCAL_VERSION_MS,
                          MIN_WAIT_BEFORE_RELOAD_LOCAL_VERSION_MS_DEFAULT));
      pushWaitBeforeReloadLocalVersionMs =
          memoize(
              () ->
                  cfg.get()
                      .getInt(
                          REPLICATION_FILTER_SECTION,
                          REPLICATION_PUSH_FILTER_SUBSECTION,
                          RANDOM_WAIT_MAX_BOUND_BEFORE_RELOAD_LOCAL_VERSION_MS,
                          RANDOM_WAIT_MAX_BOUND_BEFORE_RELOAD_LOCAL_VERSION_MS_DEFAULT));
    }

    public boolean isFetchFilterRandomSleepEnabled() {
      return fetchWaitBeforeReloadLocalVersionMs.get() != 0;
    }

    public Integer fetchFilterRandomSleepTimeMs() {
      return fetchMinWaitBeforeReloadLocalVersionMs.get()
          + new Random().nextInt(fetchWaitBeforeReloadLocalVersionMs.get());
    }

    public boolean isPushFilterRandomSleepEnabled() {
      return pushWaitBeforeReloadLocalVersionMs.get() != 0;
    }

    public Integer pushFilterRandomSleepTimeMs() {
      return pushMinWaitBeforeReloadLocalVersionMs.get()
          + new Random().nextInt(pushWaitBeforeReloadLocalVersionMs.get());
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
