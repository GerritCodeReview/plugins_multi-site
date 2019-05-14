// Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.googlesource.gerrit.plugins.multisite.ConfigurationHelper.getBoolean;
import static com.googlesource.gerrit.plugins.multisite.ConfigurationHelper.getConfigFile;
import static com.googlesource.gerrit.plugins.multisite.ConfigurationHelper.getInt;
import static com.googlesource.gerrit.plugins.multisite.ConfigurationHelper.getList;
import static com.googlesource.gerrit.plugins.multisite.ConfigurationHelper.getLong;
import static com.googlesource.gerrit.plugins.multisite.ConfigurationHelper.getString;
import static com.googlesource.gerrit.plugins.multisite.ConfigurationHelper.lazyLoad;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement.EnforcePolicy;
import org.apache.commons.lang.StringUtils;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.BoundedExponentialBackoffRetry;
import org.eclipse.jgit.lib.Config;

public class ZookeeperConfig {
  public static final String ZOOKEEPER_MS_CONFIG = "zookeeper-multi-site.config";
  public static final String SECTION = "ref-database";
  public static final int defaultSessionTimeoutMs;
  public static final int defaultConnectionTimeoutMs;
  public static final String DEFAULT_ZK_CONNECT = "localhost:2181";
  private final int DEFAULT_RETRY_POLICY_BASE_SLEEP_TIME_MS = 1000;
  private final int DEFAULT_RETRY_POLICY_MAX_SLEEP_TIME_MS = 3000;
  private final int DEFAULT_RETRY_POLICY_MAX_RETRIES = 3;
  private final int DEFAULT_CAS_RETRY_POLICY_BASE_SLEEP_TIME_MS = 100;
  private final int DEFAULT_CAS_RETRY_POLICY_MAX_SLEEP_TIME_MS = 300;
  private final int DEFAULT_CAS_RETRY_POLICY_MAX_RETRIES = 3;
  private final int DEFAULT_TRANSACTION_LOCK_TIMEOUT = 1000;

  static {
    CuratorFrameworkFactory.Builder b = CuratorFrameworkFactory.builder();
    defaultSessionTimeoutMs = b.getSessionTimeoutMs();
    defaultConnectionTimeoutMs = b.getConnectionTimeoutMs();
  }

  static final String ENABLE_KEY = "enabled";
  public static final String SUBSECTION = "zookeeper";
  public static final String KEY_CONNECT_STRING = "connectString";
  public static final String KEY_SESSION_TIMEOUT_MS = "sessionTimeoutMs";
  public static final String KEY_CONNECTION_TIMEOUT_MS = "connectionTimeoutMs";
  public static final String KEY_RETRY_POLICY_BASE_SLEEP_TIME_MS = "retryPolicyBaseSleepTimeMs";
  public static final String KEY_RETRY_POLICY_MAX_SLEEP_TIME_MS = "retryPolicyMaxSleepTimeMs";
  public static final String KEY_RETRY_POLICY_MAX_RETRIES = "retryPolicyMaxRetries";
  public static final String KEY_ROOT_NODE = "rootNode";
  public final String KEY_CAS_RETRY_POLICY_BASE_SLEEP_TIME_MS = "casRetryPolicyBaseSleepTimeMs";
  public final String KEY_CAS_RETRY_POLICY_MAX_SLEEP_TIME_MS = "casRetryPolicyMaxSleepTimeMs";
  public final String KEY_CAS_RETRY_POLICY_MAX_RETRIES = "casRetryPolicyMaxRetries";
  public final String TRANSACTION_LOCK_TIMEOUT_KEY = "transactionLockTimeoutMs";

  public static final String SUBSECTION_ENFORCEMENT_RULES = "enforcementRules";

  private final String connectionString;
  private final String root;
  private final int sessionTimeoutMs;
  private final int connectionTimeoutMs;
  private final int baseSleepTimeMs;
  private final int maxSleepTimeMs;
  private final int maxRetries;
  private final int casBaseSleepTimeMs;
  private final int casMaxSleepTimeMs;
  private final int casMaxRetries;
  private final boolean enabled;

  private final Multimap<EnforcePolicy, String> enforcementRules;

  private final Long transactionLockTimeOut;

  private CuratorFramework build;

  @Inject
  ZookeeperConfig(SitePaths sitePaths) {
    this(getConfigFile(sitePaths, ZOOKEEPER_MS_CONFIG));
  }

  @VisibleForTesting
  public ZookeeperConfig(Config zkCfg) {
    Supplier<Config> lazyZkConfig = lazyLoad(zkCfg);
    connectionString =
        getString(lazyZkConfig, SECTION, SUBSECTION, KEY_CONNECT_STRING, DEFAULT_ZK_CONNECT);
    root = getString(lazyZkConfig, SECTION, SUBSECTION, KEY_ROOT_NODE, "gerrit/multi-site");
    sessionTimeoutMs =
        getInt(lazyZkConfig, SECTION, SUBSECTION, KEY_SESSION_TIMEOUT_MS, defaultSessionTimeoutMs);
    connectionTimeoutMs =
        getInt(
            lazyZkConfig,
            SECTION,
            SUBSECTION,
            KEY_CONNECTION_TIMEOUT_MS,
            defaultConnectionTimeoutMs);

    baseSleepTimeMs =
        getInt(
            lazyZkConfig,
            SECTION,
            SUBSECTION,
            KEY_RETRY_POLICY_BASE_SLEEP_TIME_MS,
            DEFAULT_RETRY_POLICY_BASE_SLEEP_TIME_MS);

    maxSleepTimeMs =
        getInt(
            lazyZkConfig,
            SECTION,
            SUBSECTION,
            KEY_RETRY_POLICY_MAX_SLEEP_TIME_MS,
            DEFAULT_RETRY_POLICY_MAX_SLEEP_TIME_MS);

    maxRetries =
        getInt(
            lazyZkConfig,
            SECTION,
            SUBSECTION,
            KEY_RETRY_POLICY_MAX_RETRIES,
            DEFAULT_RETRY_POLICY_MAX_RETRIES);

    casBaseSleepTimeMs =
        getInt(
            lazyZkConfig,
            SECTION,
            SUBSECTION,
            KEY_CAS_RETRY_POLICY_BASE_SLEEP_TIME_MS,
            DEFAULT_CAS_RETRY_POLICY_BASE_SLEEP_TIME_MS);

    casMaxSleepTimeMs =
        getInt(
            lazyZkConfig,
            SECTION,
            SUBSECTION,
            KEY_CAS_RETRY_POLICY_MAX_SLEEP_TIME_MS,
            DEFAULT_CAS_RETRY_POLICY_MAX_SLEEP_TIME_MS);

    casMaxRetries =
        getInt(
            lazyZkConfig,
            SECTION,
            SUBSECTION,
            KEY_CAS_RETRY_POLICY_MAX_RETRIES,
            DEFAULT_CAS_RETRY_POLICY_MAX_RETRIES);

    transactionLockTimeOut =
        getLong(
            lazyZkConfig,
            SECTION,
            SUBSECTION,
            TRANSACTION_LOCK_TIMEOUT_KEY,
            DEFAULT_TRANSACTION_LOCK_TIMEOUT);

    checkArgument(StringUtils.isNotEmpty(connectionString), "zookeeper.%s contains no servers");

    enabled = getBoolean(lazyZkConfig, SECTION, null, ENABLE_KEY, true);

    enforcementRules = MultimapBuilder.hashKeys().arrayListValues().build();
    for (EnforcePolicy policy : EnforcePolicy.values()) {
      enforcementRules.putAll(
          policy, getList(lazyZkConfig, SECTION, SUBSECTION_ENFORCEMENT_RULES, policy.name()));
    }
  }

  public CuratorFramework buildCurator() {
    if (build == null) {
      this.build =
          CuratorFrameworkFactory.builder()
              .connectString(connectionString)
              .sessionTimeoutMs(sessionTimeoutMs)
              .connectionTimeoutMs(connectionTimeoutMs)
              .retryPolicy(
                  new BoundedExponentialBackoffRetry(baseSleepTimeMs, maxSleepTimeMs, maxRetries))
              .namespace(root)
              .build();
      this.build.start();
    }

    return this.build;
  }

  public Long getZkInterProcessLockTimeOut() {
    return transactionLockTimeOut;
  }

  public RetryPolicy buildCasRetryPolicy() {
    return new BoundedExponentialBackoffRetry(casBaseSleepTimeMs, casMaxSleepTimeMs, casMaxRetries);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Multimap<EnforcePolicy, String> getEnforcementRules() {
    return enforcementRules;
  }
}
