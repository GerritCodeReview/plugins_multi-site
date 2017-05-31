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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HttpClientProviderTest {
  private static final int TIME_INTERVAL = 1000;
  private static final String EMPTY = "";

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Configuration configMock;

  @Before
  public void setUp() throws Exception {
    when(configMock.http().user()).thenReturn(EMPTY);
    when(configMock.http().password()).thenReturn(EMPTY);
    when(configMock.http().connectionTimeout()).thenReturn(TIME_INTERVAL);
    when(configMock.http().socketTimeout()).thenReturn(TIME_INTERVAL);
  }

  @Test
  public void testGet() throws Exception {
    Injector injector = Guice.createInjector(new TestModule());
    try (CloseableHttpClient httpClient1 = injector.getInstance(CloseableHttpClient.class)) {
      assertThat(httpClient1).isNotNull();
      try (CloseableHttpClient httpClient2 = injector.getInstance(CloseableHttpClient.class)) {
        assertThat(httpClient1).isEqualTo(httpClient2);
      }
    }
  }

  class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(Configuration.class).toInstance(configMock);
      bind(CloseableHttpClient.class).toProvider(HttpClientProvider.class).in(Scopes.SINGLETON);
    }
  }
}
