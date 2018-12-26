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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.HttpResponseHandler.HttpResult;
import com.google.common.base.Charsets;
import com.google.common.base.Supplier;
import com.google.common.net.MediaType;
import com.google.gerrit.server.events.SupplierSerializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URI;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;

class HttpSession {
  private final CloseableHttpClient httpClient;
  private final Gson gson =
      new GsonBuilder().registerTypeAdapter(Supplier.class, new SupplierSerializer()).create();

  @Inject
  HttpSession(CloseableHttpClient httpClient) {
    this.httpClient = httpClient;
  }

  HttpResult post(String endpoint) throws IOException {
    return post(endpoint, null);
  }

  HttpResult post(String uri, Object content) throws IOException {
    HttpPost post = new HttpPost(uri);
    setContent(post, content);
    return httpClient.execute(post, new HttpResponseHandler());
  }

  HttpResult delete(String uri) throws IOException {
    return httpClient.execute(new HttpDelete(uri), new HttpResponseHandler());
  }

  HttpResult delete(String uri, Object content) throws IOException {
    HttpDeleteWithBody delete = new HttpDeleteWithBody(uri);
    setContent(delete, content);
    return httpClient.execute(delete, new HttpResponseHandler());
  }

  private void setContent(HttpEntityEnclosingRequestBase request, Object content) {
    if (content != null) {
      request.addHeader("Content-Type", MediaType.JSON_UTF_8.toString());
      request.setEntity(new StringEntity(jsonEncode(content), Charsets.UTF_8));
    }
  }

  private String jsonEncode(Object content) {
    if (content instanceof String) {
      return (String) content;
    }
    return gson.toJson(content);
  }

  private class HttpDeleteWithBody extends HttpEntityEnclosingRequestBase {
    @Override
    public String getMethod() {
      return HttpDelete.METHOD_NAME;
    }

    private HttpDeleteWithBody(String uri) {
      setURI(URI.create(uri));
    }
  }
}
