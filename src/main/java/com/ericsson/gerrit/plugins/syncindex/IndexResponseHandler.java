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

import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.ericsson.gerrit.plugins.syncindex.IndexResponseHandler.IndexResult;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

class IndexResponseHandler implements ResponseHandler<IndexResult> {

  static class IndexResult {
    private boolean successful;
    private String message;

    IndexResult(boolean successful, String message) {
      this.successful = successful;
      this.message = message;
    }

    boolean isSuccessful() {
      return successful;
    }

    String getMessage() {
      return message;
    }
  }

  private static final Logger log = LoggerFactory
      .getLogger(IndexResponseHandler.class);

  @Override
  public IndexResult handleResponse(HttpResponse response) {
    return new IndexResult(isSuccessful(response), parseResponse(response));
  }

  private boolean isSuccessful(HttpResponse response) {
    return response.getStatusLine().getStatusCode() == SC_NO_CONTENT;
  }

  private String parseResponse(HttpResponse response) {
    HttpEntity entity = response.getEntity();
    String asString = "";
    if (entity != null) {
      try {
        asString = EntityUtils.toString(entity);
      } catch (IOException e) {
        log.error("Error parsing entity", e);
      }
    }
    return asString;
  }
}
