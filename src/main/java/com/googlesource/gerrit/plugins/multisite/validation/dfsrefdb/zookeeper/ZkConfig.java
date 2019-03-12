// Copyright (C) 2012 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper;

import java.io.Serializable;
import java.util.List;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.eclipse.jgit.lib.Config;

/**
 * Configuration for a Zookeeper setup.
 */
public class ZkConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_SESSION_TIMEOUT_MS;

    public static final int DEFAULT_CONNECTION_TIMEOUT_MS;

    static {
        CuratorFrameworkFactory.Builder b = CuratorFrameworkFactory.builder();
        DEFAULT_SESSION_TIMEOUT_MS = b.getSessionTimeoutMs();
        DEFAULT_CONNECTION_TIMEOUT_MS = b.getConnectionTimeoutMs();
    }

    private static final String SECTION = "zookeeper";

    private static final String KEY_CONNECT_STRING = "connectString";

    private static final String KEY_SESSION_TIMEOUT = "sessionTimeout";

    private static final String KEY_CONNECTION_TIMEOUT = "connectionTimeout";

    // TODO(dborowitz): Configure RetryPolicy.

    /**
     * Get the Zookeeper root for the ref database configuration.
     *
     * @param cfg config object.
     * @return Zookeeper namespace root.
     */
    public static String getZookeeperRoot(Config cfg) {
        return MoreObjects.firstNonNull(
                cfg.getString("refdb", "zookeeper", "root"),
                "/gerrit/refdb");
    }

    /**
     * Get the config name used by the Zookeeper ref database.
     *
     * @param cfg config object.
     * @return name corresponding to a zookeeper configuration section.
     */
    public static String getZookeeperConfigName(Config cfg) {
        return cfg.getString("refdb", "zookeeper", "name");
    }

    /**
     * Get all Zookeeper configs from the given config.
     *
     * @param cfg config object.
     * @return list of configs for all "zookeeper" sections.
     */
    public static List<ZkConfig> getAllZookeeperConfigs(Config cfg) {
        List<String> names = Ordering.natural().sortedCopy(
                cfg.getSubsections(SECTION));
        List<ZkConfig> result = Lists.newArrayListWithCapacity(names.size());
        for (String name : names) {
            result.add(new ZkConfig(cfg, name));
        }
        return result;
    }

    private final String connectString;
    private final int sessionTimeoutMs;
    private final int connectionTimeoutMs;

    ZkConfig(Config cfg, String name) {
        this.connectString = cfg.getString(SECTION, name, KEY_CONNECT_STRING);
        this.sessionTimeoutMs = cfg.getInt(SECTION, name, KEY_SESSION_TIMEOUT,
                DEFAULT_SESSION_TIMEOUT_MS);
        this.connectionTimeoutMs = cfg.getInt(SECTION, name, KEY_CONNECTION_TIMEOUT,
                DEFAULT_CONNECTION_TIMEOUT_MS);
    }

    /** @return Zookeeper connection string. */
    public String getConnectString() {
        return connectString;
    }

    /** @return Zookeeper session timeout. */
    public int getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    /** @return Zookeeper connection timeout. */
    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }
}