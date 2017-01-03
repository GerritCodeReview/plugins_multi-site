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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.syncindex.IndexResponseHandler.IndexResult;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.entity.StringEntity;
import org.junit.Before;
import org.junit.Test;

import java.io.UnsupportedEncodingException;

public class IndexResponseHandlerTest {
  private static final int ERROR = 400;
  private static final int OK = 204;
  private static final String EMPTY_ENTITY = "";
  private static final String ERROR_ENTITY = "Error";

  private IndexResponseHandler handler;

  @Before
  public void setUp() throws Exception {
    handler = new IndexResponseHandler();
  }

  @Test
  public void testIsSuccessful() throws Exception {
    HttpResponse response = setupMocks(OK, EMPTY_ENTITY);
    IndexResult result = handler.handleResponse(response);
    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getMessage()).isEmpty();
  }

  @Test
  public void testIsNotSuccessful() throws Exception {
    HttpResponse response = setupMocks(ERROR, ERROR_ENTITY);
    IndexResult result = handler.handleResponse(response);
    assertThat(result.isSuccessful()).isFalse();
    assertThat(result.getMessage()).contains(ERROR_ENTITY);
  }

  private HttpResponse setupMocks(int httpCode, String entity)
      throws UnsupportedEncodingException {
    StatusLine status = mock(StatusLine.class);
    when(status.getStatusCode()).thenReturn(httpCode);
    HttpResponse response = mock(HttpResponse.class);
    when(response.getStatusLine()).thenReturn(status);
    when(response.getEntity()).thenReturn(new StringEntity(entity));
    return response;
  }
}
