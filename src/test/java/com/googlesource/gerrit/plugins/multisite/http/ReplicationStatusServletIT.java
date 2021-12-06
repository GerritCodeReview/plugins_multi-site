// Copyright (C) 2021 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.http;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.truth.Truth.assertThat;
import static com.googlesource.gerrit.plugins.multisite.http.HttpModule.LAG_ENDPOINT_SEGMENT;

import com.gerritforge.gerrit.globalrefdb.validation.Log4jSharedRefLogger;
import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDbConfiguration;
import com.gerritforge.gerrit.globalrefdb.validation.SharedRefLogger;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.inject.AbstractModule;
import com.googlesource.gerrit.plugins.multisite.Log4jProjectVersionLogger;
import com.googlesource.gerrit.plugins.multisite.ProjectVersionLogger;
import com.googlesource.gerrit.plugins.multisite.cache.CacheModule;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwarderModule;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.RouterModule;
import com.googlesource.gerrit.plugins.multisite.index.IndexModule;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

@TestPlugin(
    name = "multi-site",
    sysModule =
        "com.googlesource.gerrit.plugins.multisite.http.ReplicationStatusServletIT$TestModule",
    httpModule = "com.googlesource.gerrit.plugins.multisite.http.HttpModule")
public class ReplicationStatusServletIT extends LightweightPluginDaemonTest {
  private static final String APPLICATION_JSON = "application/json";
  private static final String LAG_ENDPOINT =
      String.format("/plugins/multi-site/%s", LAG_ENDPOINT_SEGMENT);

  public static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      install(new ForwarderModule());
      install(new CacheModule());
      install(new RouterModule());
      install(new IndexModule());
      SharedRefDbConfiguration sharedRefDbConfig =
          new SharedRefDbConfiguration(new Config(), "multi-site");
      bind(SharedRefDbConfiguration.class).toInstance(sharedRefDbConfig);
      bind(ProjectVersionLogger.class).to(Log4jProjectVersionLogger.class);
      bind(SharedRefLogger.class).to(Log4jSharedRefLogger.class);
    }
  }

  @Test
  public void shouldSucceedForAdminUsers() throws Exception {
    RestResponse result = adminRestSession.get(LAG_ENDPOINT);

    result.assertOK();
    assertThat(result.getHeader(CONTENT_TYPE)).contains(APPLICATION_JSON);
  }

  @Test
  public void shouldFailWhenUserHasNoAdminServerCapability() throws Exception {
    RestResponse result = userRestSession.get(LAG_ENDPOINT);
    result.assertForbidden();
    assertThat(result.getEntityContent()).contains("not permitted");
  }
}
