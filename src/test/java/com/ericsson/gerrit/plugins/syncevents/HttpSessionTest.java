// Copyright (C) 2015 Ericsson
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

package com.ericsson.gerrit.plugins.syncevents;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.syncevents.SyncEventsResponseHandler.SyncResult;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class HttpSessionTest {
  private static final int MAX_TRIES = 3;
  private static final int RETRY_INTERVAL = 250;
  private static final int TIMEOUT = 500;
  private static final int ERROR = 500;
  private static final int OK = 204;
  private static final int NOT_FOUND = 404;
  private static final int UNAUTHORIZED = 401;

  private static final String ENDPOINT = "/plugins/sync-events/event";
  private static final String BODY = "SerializedEvent";
  private static final String ERROR_MESSAGE = "Error message";
  private static final String REQUEST_MADE = "Request made";
  private static final String RETRY_AT_ERROR = "Retry at error";
  private static final String RETRY_AT_DELAY = "Retry at delay";

  private HttpSession httpSession;

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(0);

  @Before
  public void setUp() throws Exception {
    String url = "http://localhost:" + wireMockRule.port();
    Configuration cfg = mock(Configuration.class);
    when(cfg.getUrl()).thenReturn(url);
    when(cfg.getUser()).thenReturn("user");
    when(cfg.getPassword()).thenReturn("pass");
    when(cfg.getMaxTries()).thenReturn(MAX_TRIES);
    when(cfg.getConnectionTimeout()).thenReturn(TIMEOUT);
    when(cfg.getSocketTimeout()).thenReturn(TIMEOUT);
    when(cfg.getRetryInterval()).thenReturn(RETRY_INTERVAL);

    httpSession =
        new HttpSession(new HttpClientProvider(cfg).get(), url);
  }

  @Test
  public void testResponseOK() throws Exception {
    stubFor(post(urlEqualTo(ENDPOINT)).willReturn(aResponse().withStatus(OK)));
    assertThat(httpSession.post(ENDPOINT, BODY).isSuccessful()).isTrue();
  }

  @Test
  public void testResponseOKEmptyBody() throws Exception {
    stubFor(post(urlEqualTo(ENDPOINT)).willReturn(aResponse().withStatus(OK)));
    assertThat(httpSession.post(ENDPOINT, "").isSuccessful()).isTrue();
  }

  @Test
  public void testNotAuthorized() throws Exception {
    String expected = "unauthorized";
    stubFor(post(urlEqualTo(ENDPOINT)).willReturn(
        aResponse().withStatus(UNAUTHORIZED).withBody(expected)));

    SyncResult result = httpSession.post(ENDPOINT, BODY);
    assertThat(result.isSuccessful()).isFalse();
    assertThat(result.getMessage()).isEqualTo(expected);
  }

  @Test
  public void testNotFound() throws Exception {
    String expected = "not found";
    stubFor(post(urlEqualTo(ENDPOINT)).willReturn(
        aResponse().withStatus(NOT_FOUND).withBody(expected)));

    SyncResult result = httpSession.post(ENDPOINT, BODY);
    assertThat(result.isSuccessful()).isFalse();
    assertThat(result.getMessage()).isEqualTo(expected);
  }

  @Test
  public void testBadResponseRetryThenOK() throws Exception {
    stubFor(post(urlEqualTo(ENDPOINT)).inScenario(RETRY_AT_ERROR)
        .whenScenarioStateIs(Scenario.STARTED).willSetStateTo(REQUEST_MADE)
        .willReturn(aResponse().withStatus(ERROR)));
    stubFor(post(urlEqualTo(ENDPOINT)).inScenario(RETRY_AT_ERROR)
        .whenScenarioStateIs(REQUEST_MADE)
        .willReturn(aResponse().withStatus(OK)));

    assertThat(httpSession.post(ENDPOINT, BODY).isSuccessful()).isTrue();
  }

  @Test
  public void testBadResponseRetryThenGiveUp() throws Exception {
    stubFor(post(urlEqualTo(ENDPOINT)).willReturn(
        aResponse().withStatus(ERROR).withBody(ERROR_MESSAGE)));

    assertThat(httpSession.post(ENDPOINT, BODY).isSuccessful()).isFalse();
    assertThat(httpSession.post(ENDPOINT, BODY).getMessage())
        .isEqualTo(ERROR_MESSAGE);
  }

  @Test
  public void testRetryAfterDelay() throws Exception {
    stubFor(post(urlEqualTo(ENDPOINT)).inScenario(RETRY_AT_DELAY)
        .whenScenarioStateIs(Scenario.STARTED).willSetStateTo(REQUEST_MADE)
        .willReturn(aResponse().withStatus(ERROR).withFixedDelay(TIMEOUT / 2)));
    stubFor(post(urlEqualTo(ENDPOINT)).inScenario(RETRY_AT_DELAY)
        .whenScenarioStateIs(REQUEST_MADE)
        .willReturn(aResponse().withStatus(OK)));

    assertThat(httpSession.post(ENDPOINT, BODY).isSuccessful()).isTrue();
  }

  @Test
  public void testGiveUpAtTimeout() throws Exception {
    stubFor(post(urlEqualTo(ENDPOINT)).inScenario(RETRY_AT_DELAY)
        .whenScenarioStateIs(Scenario.STARTED).willSetStateTo(REQUEST_MADE)
        .willReturn(aResponse().withStatus(ERROR).withFixedDelay(TIMEOUT)));

    assertThat(httpSession.post(ENDPOINT, BODY).isSuccessful()).isFalse();
  }

  @Test
  public void testResponseWithMalformedResponse() throws Exception {
    stubFor(post(urlEqualTo(ENDPOINT)).willReturn(
        aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

    assertThat(httpSession.post(ENDPOINT, BODY).isSuccessful()).isFalse();
  }
}
