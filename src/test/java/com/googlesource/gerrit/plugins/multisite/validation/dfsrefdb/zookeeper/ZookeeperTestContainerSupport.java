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

import java.io.IOException;
import org.apache.curator.framework.CuratorFramework;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@Ignore
public abstract class ZookeeperTestContainerSupport {

  GenericContainer container;
  CuratorFramework curator;
  ZkRefInfoDAO marshaller;

  @Before
  public void setUp() throws IOException {
    container = new GenericContainer("zookeeper:latest");
    container.addExposedPorts(2181);
    container.waitingFor(Wait.forListeningPort());
    container.start();

    Integer zkHostPort = container.getMappedPort(2181);
    String connectString = "localhost:" + zkHostPort;
    ZkConfig zkConfig = new ZkConfig(connectString, "root", 1000, 5000);
    curator = new CuratorFrameworkBuilder().config(zkConfig).build();
    curator.start();
    marshaller = new ZkRefInfoDAO(curator);
  }

  @After
  public void cleanup() {
    container.stop();
    curator.delete();
  }
}
