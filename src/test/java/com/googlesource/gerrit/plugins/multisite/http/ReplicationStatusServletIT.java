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
import com.google.gerrit.entities.Project;
import com.google.gerrit.httpd.restapi.RestApiServlet;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.multibindings.OptionalBinder;
import com.googlesource.gerrit.plugins.multisite.Log4jProjectVersionLogger;
import com.googlesource.gerrit.plugins.multisite.ProjectVersionLogger;
import com.googlesource.gerrit.plugins.multisite.cache.CacheModule;
import com.googlesource.gerrit.plugins.multisite.consumer.ReplicationStatus;
import com.googlesource.gerrit.plugins.multisite.consumer.ReplicationStatusModule;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwarderModule;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.RouterModule;
import com.googlesource.gerrit.plugins.multisite.index.IndexModule;
import com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdate;
import com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdateImpl;
import java.io.IOException;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
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
  private ReplicationStatus replicationStatus;

  public static class TestModule extends AbstractModule {
    @Inject WorkQueue workQueue;

    @Override
    protected void configure() {
      install(new ForwarderModule());
      install(new CacheModule());
      install(new RouterModule());
      install(new IndexModule());
      install(new ReplicationStatusModule(workQueue));
      SharedRefDbConfiguration sharedRefDbConfig =
          new SharedRefDbConfiguration(new Config(), "multi-site");
      bind(SharedRefDbConfiguration.class).toInstance(sharedRefDbConfig);
      bind(ProjectVersionLogger.class).to(Log4jProjectVersionLogger.class);
      bind(SharedRefLogger.class).to(Log4jSharedRefLogger.class);
      OptionalBinder.newOptionalBinder(binder(), ProjectVersionRefUpdate.class)
          .setBinding()
          .to(ProjectVersionRefUpdateImpl.class)
          .in(Scopes.SINGLETON);
    }
  }

  @Before
  public void setUp() throws IOException {
    replicationStatus = plugin.getSysInjector().getInstance(ReplicationStatus.class);
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

  @Test
  public void shouldReturnCurrentProjectLag() throws Exception {
    replicationStatus.doUpdateLag(Project.nameKey("foo"), 123L);

    RestResponse result = adminRestSession.get(LAG_ENDPOINT);

    result.assertOK();
    assertThat(contentWithoutMagicJson(result)).isEqualTo("{\"foo\":123}");
  }

  @Test
  public void shouldReturnProjectsOrderedDescendinglyByLag() throws Exception {
    replicationStatus.doUpdateLag(Project.nameKey("bar"), 123L);
    replicationStatus.doUpdateLag(Project.nameKey("foo"), 3L);
    replicationStatus.doUpdateLag(Project.nameKey("baz"), 52300L);

    RestResponse result = adminRestSession.get(LAG_ENDPOINT);

    result.assertOK();
    assertThat(contentWithoutMagicJson(result)).isEqualTo("{\"baz\":52300,\"bar\":123,\"foo\":3}");
  }

  @Test
  public void shouldHonourTheLimitParameter() throws Exception {
    replicationStatus.doUpdateLag(Project.nameKey("bar"), 1L);
    replicationStatus.doUpdateLag(Project.nameKey("foo"), 2L);
    replicationStatus.doUpdateLag(Project.nameKey("baz"), 3L);

    RestResponse result = adminRestSession.get(String.format("%s?limit=2", LAG_ENDPOINT));

    result.assertOK();
    assertThat(contentWithoutMagicJson(result)).isEqualTo("{\"baz\":3,\"foo\":2}");
  }

  private String contentWithoutMagicJson(RestResponse response) throws IOException {
    return response.getEntityContent().substring(RestApiServlet.JSON_MAGIC.length);
  }
}
