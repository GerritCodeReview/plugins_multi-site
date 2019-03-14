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
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.junit.Ignore;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import static com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.ZkSharedRefDatabase.pathFor;
import static com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.ZkSharedRefDatabase.writeObjectId;

@Ignore
public class ZookeeperTestContainerSupport {

  private GenericContainer container;
  private Config splitBrainconfig;
  private CuratorFramework curator;

  public String getConnectString() {
    return connectString;
  }

  private String connectString;

  public CuratorFramework getCurator() {
    return curator;
  }

  public GenericContainer getContainer() {
    return container;
  }

  public Config getSplitBrainconfig() {
    return splitBrainconfig;
  }

  public ZookeeperTestContainerSupport() {
    container = new GenericContainer("zookeeper:latest")
            .withExposedPorts(2181)
            .waitingFor(Wait.forListeningPort());
    container.start();
    Integer zkHostPort = container.getMappedPort(2181);
    splitBrainconfig = new Config();
    this.connectString = "localhost:" + zkHostPort;
    this.splitBrainconfig.setBoolean("split-brain", null, "enabled", true);
    this.splitBrainconfig.setString("split-brain", "zookeeper", "connectString", connectString);

    this.curator =
        new Configuration(splitBrainconfig).getSplitBrain().getZookeeper().buildCurator();
    this.curator.start();
  }

  public void cleanup() {
    this.container.stop();
    this.curator.delete();
  }

  public ObjectId readRefValueFromZk(String projectName, Ref ref) throws Exception {
    final byte[] bytes = curator.getData().forPath(pathFor(projectName, ref));
    return ZkSharedRefDatabase.readObjectId(bytes);
  }

  public void createRefInZk(String projectName, Ref ref) throws Exception {
    curator.create().creatingParentContainersIfNeeded().forPath(pathFor(projectName, ref), writeObjectId(ref.getObjectId()));
  }
}
