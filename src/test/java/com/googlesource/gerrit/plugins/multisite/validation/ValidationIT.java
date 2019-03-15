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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.LogThreshold;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.inject.AbstractModule;
import org.junit.Test;

@NoHttpd
@LogThreshold(level = "INFO")
@TestPlugin(
    name = "multi-site",
    sysModule = "com.googlesource.gerrit.plugins.multisite.validation.ValidationIT$Module")
public class ValidationIT extends LightweightPluginDaemonTest {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      install(new ValidationModule());
    }
  }

  @Test
  public void inSyncChangeValidatorShouldAcceptNewChange() throws Exception {
    final PushOneCommit.Result change = createChange("refs/for/master");

    change.assertOkStatus();
  }
}
