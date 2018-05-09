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
import static com.google.common.truth.Truth.assertThat;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gerrit.acceptance.GlobalPluginConfig;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

@Ignore
@NoHttpd
@TestPlugin(
  name = "high-availability",
  sysModule = "com.ericsson.gerrit.plugins.highavailability.Module",
  httpModule = "com.ericsson.gerrit.plugins.highavailability.HttpModule"
)
public abstract class AbstractIndexForwardingIT extends LightweightPluginDaemonTest {
  private static final int PORT = 18889;
  private static final String URL = "http://localhost:" + PORT;

  @Rule public WireMockRule wireMockRule = new WireMockRule(options().port(PORT), false);

  @Before
  public void before() throws Exception {
    beforeAction();
  }

  @Test
  @UseLocalDisk
  @GlobalPluginConfig(pluginName = "high-availability", name = "peerInfo.static.url", value = URL)
  @GlobalPluginConfig(pluginName = "high-availability", name = "http.retryInterval", value = "100")
  public void testIndexForwarding() throws Exception {
    String expectedRequest = getExpectedRequest();
    CountDownLatch expectedRequestLatch = new CountDownLatch(1);
    wireMockRule.addMockServiceRequestListener(
        (request, response) -> {
          if (request.getAbsoluteUrl().contains(expectedRequest)) {
            expectedRequestLatch.countDown();
          }
        });
    givenThat(
        post(urlEqualTo(expectedRequest))
            .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));
    doAction();
    assertThat(expectedRequestLatch.await(5, TimeUnit.SECONDS)).isTrue();
    verify(postRequestedFor(urlEqualTo(expectedRequest)));
  }

  /** Perform pre-test setup. */
  protected abstract void beforeAction() throws Exception;

  /**
   * Get the URL on which a request is expected.
   *
   * @return the URL.
   */
  protected abstract String getExpectedRequest();

  /** Perform the action that should cause an index operation to occur. */
  protected abstract void doAction() throws Exception;
}
