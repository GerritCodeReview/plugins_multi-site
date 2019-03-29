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

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.LogThreshold;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.Module;
import com.googlesource.gerrit.plugins.multisite.NoteDbStatus;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.ZookeeperTestContainerSupport;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
@LogThreshold(level = "INFO")
@TestPlugin(
    name = "multi-site",
    sysModule =
        "com.googlesource.gerrit.plugins.multisite.validation.ValidationIT$ZookeeperTestModule")
public class ValidationIT extends LightweightPluginDaemonTest {

  static {
    System.setProperty("gerrit.notedb", "ON");
  }

  public static class ZookeeperTestModule extends LifecycleModule {

    public class ZookeeperStopAtShutdown implements LifecycleListener {
      private final ZookeeperTestContainerSupport zookeeperContainer;

      public ZookeeperStopAtShutdown(ZookeeperTestContainerSupport zk) {
        this.zookeeperContainer = zk;
      }

      @Override
      public void stop() {
        zookeeperContainer.cleanup();
      }

      @Override
      public void start() {
        // Do nothing
      }
    }

    private final NoteDbStatus noteDb;

    @Inject
    public ZookeeperTestModule(NoteDbStatus noteDb) {
      this.noteDb = noteDb;
    }

    @Override
    protected void configure() {
      ZookeeperTestContainerSupport zookeeperContainer = new ZookeeperTestContainerSupport(true);
      Configuration multiSiteConfig = zookeeperContainer.getConfig();
      bind(Configuration.class).toInstance(multiSiteConfig);
      install(new Module(multiSiteConfig, noteDb));

      listener().toInstance(new ZookeeperStopAtShutdown(zookeeperContainer));
    }
  }

  @Override
  @Before
  public void setUpTestPlugin() throws Exception {
    super.setUpTestPlugin();

    if (!notesMigration.commitChangeWrites()) {
      throw new IllegalStateException("NoteDb is mandatory for running the multi-site plugin");
    }
  }

  @Test
  public void inSyncChangeValidatorShouldAcceptNewChange() throws Exception {
    final PushOneCommit.Result change = createChange();
    change.assertOkStatus();
    //TODO: Should check the shared database...
  }
}
