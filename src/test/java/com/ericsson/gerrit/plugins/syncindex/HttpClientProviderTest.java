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
import static org.mockito.Mockito.when;

import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Scopes;

import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HttpClientProviderTest {
  private static final int TIME_INTERVAL = 1000;
  private static final String EMPTY = "";

  @Mock
  private Configuration config;

  @Before
  public void setUp() throws Exception {
    when(config.getUrl()).thenReturn(EMPTY);
    when(config.getUser()).thenReturn(EMPTY);
    when(config.getPassword()).thenReturn(EMPTY);
    when(config.getConnectionTimeout()).thenReturn(TIME_INTERVAL);
    when(config.getSocketTimeout()).thenReturn(TIME_INTERVAL);
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
