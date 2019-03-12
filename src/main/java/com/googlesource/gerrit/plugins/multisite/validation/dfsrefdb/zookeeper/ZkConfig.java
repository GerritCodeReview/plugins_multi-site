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

package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper;

import com.google.common.base.MoreObjects;
import com.google.inject.Singleton;
import java.io.Serializable;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.eclipse.jgit.lib.Config;

/** Configuration for a Zookeeper setup. */
@Singleton
public class ZkConfig implements Serializable {
  private static final long serialVersionUID = 1L;

  public final int DEFAULT_SESSION_TIMEOUT_MS;

  public final int DEFAULT_CONNECTION_TIMEOUT_MS;

  private final String SECTION = "zookeeper";

  private final String KEY_CONNECT_STRING = "connectString";

  private final String KEY_SESSION_TIMEOUT = "sessionTimeout";

  private final String KEY_CONNECTION_TIMEOUT = "connectionTimeout";

  private final String connectString;
  private final int sessionTimeoutMs;
  private final int connectionTimeoutMs;
  private final String zookeeperRoot;

  ZkConfig(
      final String connectString,
      final String zookeeperRoot,
      final int sessionTimeoutMs,
      final int connectionTimeoutMs) {
    this.connectString = connectString;
    this.sessionTimeoutMs = sessionTimeoutMs;
    this.connectionTimeoutMs = connectionTimeoutMs;
    this.zookeeperRoot = zookeeperRoot;

    CuratorFrameworkFactory.Builder b = CuratorFrameworkFactory.builder();
    DEFAULT_SESSION_TIMEOUT_MS = b.getSessionTimeoutMs();
    DEFAULT_CONNECTION_TIMEOUT_MS = b.getConnectionTimeoutMs();
  }

  public ZkConfig fromConfig(Config cfg) {
    return new ZkConfig(
        cfg.getString(SECTION, null, KEY_CONNECT_STRING),
        MoreObjects.firstNonNull(cfg.getString("refdb", "zookeeper", "root"), "/gerrit/refdb"),
        cfg.getInt(SECTION, null, KEY_SESSION_TIMEOUT, DEFAULT_SESSION_TIMEOUT_MS),
        cfg.getInt(SECTION, null, KEY_CONNECTION_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT_MS));
  }
  /** Get the Zookeeper root for the ref database configuration. */
  public String getZookeeperRoot() {
    return zookeeperRoot;
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
