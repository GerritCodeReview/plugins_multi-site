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

package com.ericsson.gerrit.plugins.syncindex;

import static com.google.common.truth.Truth.assertThat;
import static org.easymock.EasyMock.expect;

import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Scopes;

import org.apache.http.impl.client.CloseableHttpClient;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

public class HttpClientProviderTest extends EasyMockSupport {
  private static final int TIME_INTERVAL = 1000;
  private static final String EMPTY = "";

  private Configuration config;

  @Before
  public void setUp() throws Exception {
    config = createNiceMock(Configuration.class);
    expect(config.getUrl()).andReturn(EMPTY).anyTimes();
    expect(config.getUser()).andReturn(EMPTY).anyTimes();
    expect(config.getPassword()).andReturn(EMPTY).anyTimes();
    expect(config.getMaxTries()).andReturn(1).anyTimes();
    expect(config.getConnectionTimeout()).andReturn(TIME_INTERVAL).anyTimes();
    expect(config.getSocketTimeout()).andReturn(TIME_INTERVAL).anyTimes();
    expect(config.getRetryInterval()).andReturn(TIME_INTERVAL).anyTimes();
    replayAll();
  }

  @Test
  public void testGet() throws Exception {
    Injector injector = Guice.createInjector(new TestModule());
    CloseableHttpClient httpClient1 =
        injector.getInstance(CloseableHttpClient.class);
    assertThat(httpClient1).isNotNull();
    CloseableHttpClient httpClient2 =
        injector.getInstance(CloseableHttpClient.class);
    assertThat(httpClient1).isEqualTo(httpClient2);
  }

  class TestModule extends LifecycleModule {
    @Override
    protected void configure() {
      bind(Configuration.class).toInstance(config);
      bind(CloseableHttpClient.class).toProvider(HttpClientProvider.class)
          .in(Scopes.SINGLETON);
    }
  }
}
