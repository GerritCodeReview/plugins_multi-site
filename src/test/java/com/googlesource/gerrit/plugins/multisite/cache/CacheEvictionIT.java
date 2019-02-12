// Copyright (C) 2015 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.cache;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.google.common.truth.Truth.assertThat;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gerrit.acceptance.GlobalPluginConfig;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import com.googlesource.gerrit.plugins.multisite.cache.Constants;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpStatus;
import org.junit.Rule;
import org.junit.Test;

@NoHttpd
@UseSsh
@TestPlugin(
    name = "multi-site",
    sysModule = "com.googlesource.gerrit.plugins.multisite.Module",
    httpModule = "com.googlesource.gerrit.plugins.multisite.HttpModule")
public class CacheEvictionIT extends LightweightPluginDaemonTest {
  private static final int PORT = 18888;
  private static final String URL = "http://localhost:" + PORT;

  @Rule public WireMockRule wireMockRule = new WireMockRule(options().port(PORT));

  @Override
  public void setUpTestPlugin() throws Exception {
    givenThat(any(anyUrl()).willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));
    super.setUpTestPlugin();
  }

  @Test
  @UseLocalDisk
  @GlobalPluginConfig(pluginName = "multi-site", name = "peerInfo.static.url", value = URL)
  @GlobalPluginConfig(pluginName = "multi-site", name = "http.retryInterval", value = "100")
  public void flushAndSendPost() throws Exception {
    final String flushRequest = "/plugins/multi-site/cache/" + Constants.PROJECTS;
    final CountDownLatch expectedRequestLatch = new CountDownLatch(1);
    wireMockRule.addMockServiceRequestListener(
        (request, response) -> {
          if (request.getAbsoluteUrl().contains(flushRequest)) {
            expectedRequestLatch.countDown();
          }
        });

    adminSshSession.exec("gerrit flush-caches --cache " + Constants.PROJECTS);
    assertThat(expectedRequestLatch.await(5, TimeUnit.SECONDS)).isTrue();
    verify(postRequestedFor(urlEqualTo(flushRequest)));
  }
}
