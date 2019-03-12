//Copyright(C)2012The Android Open Source Project
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

import java.io.IOException;
import java.util.List;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.RemoteConfig;

import static com.google.common.base.Preconditions.checkArgument;


public class CuratorFrameworkBuilder {
    private final List<RemoteConfig> remotes = Lists.newArrayList();

    private CuratorFramework client;

    private String root;

    private final Config config;
    
    public CuratorFrameworkBuilder(Config config) {
        this.config = config;
    }

    public CuratorFrameworkBuilder setZooKeeperRoot(String root) {
        this.root = root;
        return this;
    }

    CuratorFramework getCuratorFramework() {
        return client;
    }


    public CuratorFramework build() throws IOException {
        checkArgument(config!= null,
                "Either Config or CuratorFramework must be set");
        String zkName = ZkConfig.getZookeeperConfigName(config);
        checkArgument(zkName != null, "Config must contain refdb.zookeeper");

        ZkConfig zkCfg = new ZkConfig(config, zkName);
        checkArgument(!zkCfg.getConnectString().isEmpty(),
                "zookeeper.%s contains no servers", zkName);

        return CuratorFrameworkFactory.builder()
                .connectString(zkCfg.getConnectString())
                .sessionTimeoutMs(zkCfg.getSessionTimeoutMs())
                .connectionTimeoutMs(zkCfg.getConnectionTimeoutMs())
                .retryPolicy(new ExponentialBackoffRetry(40, 20))
                .namespace(Strings.nullToEmpty(root))
                .build();
    }
}