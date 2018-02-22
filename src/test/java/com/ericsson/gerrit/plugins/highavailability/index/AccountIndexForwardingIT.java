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

package com.ericsson.gerrit.plugins.highavailability.index;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.fail;

import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestListener;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gerrit.acceptance.GlobalPluginConfig;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpStatus;
import org.junit.Rule;
import org.junit.Test;

@NoHttpd
@UseSsh
@TestPlugin(
  name = "high-availability",
  sysModule = "com.ericsson.gerrit.plugins.highavailability.Module",
  httpModule = "com.ericsson.gerrit.plugins.highavailability.HttpModule"
)
public class AccountIndexForwardingIT extends LightweightPluginDaemonTest {
  private static final int PORT = 18889;
  private static final String URL = "http://localhost:" + PORT;

  @Rule public WireMockRule wireMockRule = new WireMockRule(options().port(PORT), false);

  @Test
  @UseLocalDisk
  @GlobalPluginConfig(
    pluginName = "high-availability",
    name = "peerInfo.strategy",
    value = "static"
  )
  @GlobalPluginConfig(pluginName = "high-availability", name = "peerInfo.static.url", value = URL)
  @GlobalPluginConfig(pluginName = "high-availability", name = "http.user", value = "admin")
  @GlobalPluginConfig(pluginName = "high-availability", name = "index.threadPoolSize", value = "10")
  @GlobalPluginConfig(
    pluginName = "high-availability",
    name = "main.sharedDirectory",
    value = "directory"
  )
  public void testAccountIndexForwarding() throws Exception {
    TestAccount testAccount = accounts.create("someUser");
    final String expectedRequest = "/plugins/high-availability/index/account/" + testAccount.id;
    final CyclicBarrier checkPoint = new CyclicBarrier(2);
    wireMockRule.addMockServiceRequestListener(
        new RequestListener() {
          @Override
          public void requestReceived(Request request, Response response) {
            if (request.getAbsoluteUrl().contains(expectedRequest)) {
              try {
                checkPoint.await();
              } catch (InterruptedException | BrokenBarrierException e) {
                fail();
              }
            }
          }
        });
    givenThat(
        post(urlEqualTo(expectedRequest))
            .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

    gApi.accounts().id(testAccount.id.get()).setActive(false);
    checkPoint.await(5, TimeUnit.SECONDS);
    verify(postRequestedFor(urlEqualTo(expectedRequest)));
  }
}
