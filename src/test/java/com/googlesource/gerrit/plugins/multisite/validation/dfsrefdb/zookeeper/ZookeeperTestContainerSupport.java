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
// Copyright (C) 2018 The Android Open Source Project
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

import com.googlesource.gerrit.plugins.multisite.Configuration;
import org.apache.curator.framework.CuratorFramework;
import org.eclipse.jgit.lib.Config;
import org.junit.Ignore;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@Ignore
public class ZookeeperTestContainerSupport {

  private GenericContainer container;
  private ZkRefInfoDAO marshaller;
  private Configuration configuration;
  private CuratorFramework curator;

  public CuratorFramework getCurator() {
    return curator;
  }

  public GenericContainer getContainer() {
    return container;
  }

  public ZkRefInfoDAO getMarshaller() {
    return marshaller;
  }

  public Configuration getConfig() {
    return configuration;
  }

  public ZookeeperTestContainerSupport() {
    container = new GenericContainer("zookeeper:latest");
    container.addExposedPorts(2181);
    container.waitingFor(Wait.forListeningPort());
    container.start();
    Integer zkHostPort = container.getMappedPort(2181);
    Config splitBrainconfig = new Config();
    String connectString = "localhost:" + zkHostPort;
    splitBrainconfig.setBoolean("split-brain", null, "enabled", true);
    splitBrainconfig.setString("split-brain", "zookeeper", "connectString", connectString);

    configuration = new Configuration(splitBrainconfig);
    this.curator = configuration.getSplitBrain().getZookeeper().buildCurator();

    this.marshaller = new ZkRefInfoDAO(this.curator);
  }

  public void cleanup() {
    this.curator.delete();
    this.container.stop();
  }
}
