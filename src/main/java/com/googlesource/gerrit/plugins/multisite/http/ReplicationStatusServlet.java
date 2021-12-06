// Copyright (C) 2021 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.http;

import com.google.common.flogger.FluentLogger;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.consumer.ReplicationStatus;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@Singleton
public class ReplicationStatusServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  protected static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String JSON_TYPE = "application/json";

  private final Gson gson;
  private final ReplicationStatus replicationStatus;

  @Inject
  ReplicationStatusServlet(Gson gson, ReplicationStatus replicationStatus) {
    this.gson = gson;
    this.replicationStatus = replicationStatus;
  }

  @Override
  protected void doGet(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
      throws ServletException, IOException {
    setResponse(httpServletResponse, 200, "{\"foo\":1}");
  }

  static void setResponse(HttpServletResponse httpResponse, int statusCode, String value)
      throws IOException {
    httpResponse.setContentType(JSON_TYPE);
    httpResponse.setStatus(statusCode);
    PrintWriter writer = httpResponse.getWriter();
    writer.print(value);
  }
}
