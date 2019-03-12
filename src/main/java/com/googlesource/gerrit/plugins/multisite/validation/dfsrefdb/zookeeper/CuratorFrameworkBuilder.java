// Copyright(C)2012The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

public class CuratorFrameworkBuilder {
  private ZkConfig config = null;

  public CuratorFrameworkBuilder config(ZkConfig config) {
    this.config = config;
    return this;
  }

  public CuratorFramework build() throws IOException {
    checkArgument(config != null, "Either Config or CuratorFramework must be set");
    checkArgument(!config.getConnectString().isEmpty(), "zookeeper.%s contains no servers");
    checkArgument(config.getZookeeperRoot() != null, "zookeeper root should be specified");
    return CuratorFrameworkFactory.builder()
        .connectString(config.getConnectString())
        .sessionTimeoutMs(config.getSessionTimeoutMs())
        .connectionTimeoutMs(config.getConnectionTimeoutMs())
        .retryPolicy(new ExponentialBackoffRetry(40, 20))
        .namespace(config.getZookeeperRoot())
        .build();
  }
}
