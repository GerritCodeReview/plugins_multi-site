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

package com.ericsson.gerrit.plugins.highavailability.peers.jgroups;

import static com.google.common.truth.Truth.assertThat;
import static java.net.InetAddress.getLocalHost;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.inject.ProvisionException;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import wiremock.com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class MyUrlProviderTest {

  private static final String HTTPD = "httpd";
  private static final String HTTPS = "https://";
  private static final String LISTEN_URL = "listenUrl";

  @Rule public ExpectedException exception = ExpectedException.none();

  @Mock(answer = RETURNS_DEEP_STUBS)
  private Configuration configurationMock;

  private Config gerritServerConfig;

  @Before
  public void setUp() throws Exception {
    gerritServerConfig = new Config();
  }

  private MyUrlProvider getMyUrlProvider() {
    return new MyUrlProvider(gerritServerConfig, configurationMock);
  }

  @Test
  public void testGetJGroupsMyUrlFromListenUrl() throws Exception {
    String hostName = getLocalHost().getHostName();

    gerritServerConfig.setString(HTTPD, null, LISTEN_URL, "https://foo:8080");
    assertThat(getMyUrlProvider().get()).isEqualTo(HTTPS + hostName + ":8080");

    gerritServerConfig.setString(HTTPD, null, LISTEN_URL, "https://foo");
    assertThat(getMyUrlProvider().get()).isEqualTo(HTTPS + hostName);

    gerritServerConfig.setString(HTTPD, null, LISTEN_URL, "https://foo/");
    assertThat(getMyUrlProvider().get()).isEqualTo(HTTPS + hostName);
  }

  @Test
  public void testGetJGroupsMyUrlFromListenUrlWhenNoListenUrlSpecified() throws Exception {
    exception.expect(ProvisionException.class);
    exception.expectMessage("exactly 1 value configured; found 0");
    getMyUrlProvider();
  }

  @Test
  public void testGetJGroupsMyUrlFromListenUrlWhenMultipleListenUrlsSpecified() throws Exception {
    gerritServerConfig.setStringList(HTTPD, null, LISTEN_URL, Lists.newArrayList("a", "b"));
    exception.expect(ProvisionException.class);
    exception.expectMessage("exactly 1 value configured; found 2");
    getMyUrlProvider();
  }

  @Test
  public void testGetJGroupsMyUrlFromListenUrlWhenReverseProxyConfigured() throws Exception {
    gerritServerConfig.setString(HTTPD, null, LISTEN_URL, "proxy-https://foo");
    exception.expect(ProvisionException.class);
    exception.expectMessage("when configured as reverse-proxy");
    getMyUrlProvider();
  }

  @Test
  public void testGetJGroupsMyUrlFromListenUrlWhenWildcardConfigured() throws Exception {
    gerritServerConfig.setString(HTTPD, null, LISTEN_URL, "https://*");
    exception.expect(ProvisionException.class);
    exception.expectMessage("when configured with wildcard");
    getMyUrlProvider();
  }

  @Test
  public void testGetJGroupsMyUrlOverridesListenUrl() throws Exception {
    when(configurationMock.peerInfoJGroups().myUrl()).thenReturn("http://somehost");
    assertThat(getMyUrlProvider().get()).isEqualTo("http://somehost");
  }
}
