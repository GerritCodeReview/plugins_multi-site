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

package com.googlesource.gerrit.plugins.multisite.validation;

import java.io.IOException;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.LogThreshold;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.git.validators.RefOperationValidationListener;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.test.TestingServer;
import org.junit.Test;

@NoHttpd
@LogThreshold(level = "INFO")
@TestPlugin(name = "multi-site", sysModule = "com.googlesource.gerrit.plugins.multisite.validation.ValidationIT$Module")
public class ValidationIT extends LightweightPluginDaemonTest {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  CuratorFramework framework;

  public static class Module extends LifecycleModule {
    public class ZookeeperStopAtShutdown implements LifecycleListener {
      private final TestingServer zookeeper;

      public ZookeeperStopAtShutdown(TestingServer zookeeper) {
        this.zookeeper = zookeeper;
      }

      @Override
      public void stop() {
        try {
          zookeeper.stop();
        } catch (IOException e) {
          logger.atWarning().withCause(e).log("Cannot start zookeeper");
          throw new RuntimeException("Cannot start zookeeper", e);
        }
      }

      @Override
      public void start() {
        try {
          zookeeper.start();
        } catch (Exception e) {
          logger.atWarning().withCause(e).log("Cannot stop zookeeper");
        }
      }
    }
    @Override
    protected void configure() {
      TestingServer zookeeper = null;
      try {
        zookeeper = new TestingServer();
      } catch (Exception e) {
        throw new RuntimeException("Cannot init zookeeper", e);
      }
      DynamicSet.bind(binder(), RefOperationValidationListener.class).to(InSyncChangeValidator.class);
      install(new ValidationModule());

      super.configure();
      listener().toInstance(new ZookeeperStopAtShutdown(zookeeper));
    }
  }

  @Test
  public void inSyncChangeValidatorShouldAcceptNewChange() throws Exception {
    final PushOneCommit.Result change = createChange("/ref/for/master");

    change.assertOkStatus();
  }
}
