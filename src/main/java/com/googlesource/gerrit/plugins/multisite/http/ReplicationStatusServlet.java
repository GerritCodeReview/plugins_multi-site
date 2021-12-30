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

import static com.google.gerrit.server.permissions.GlobalPermission.ADMINISTRATE_SERVER;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.httpd.restapi.RestApiServlet;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.consumer.ReplicationStatus;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class ReplicationStatusServlet extends HttpServlet {
  protected static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String LIMIT_RESULT_PARAMETER = "limit";
  private static final long serialVersionUID = 1L;
  private static final Integer DEFAULT_LIMIT_RESULT_PARAMETER = 10;

  private final Gson gson;
  private final ReplicationStatus replicationStatus;
  private final PermissionBackend permissionBackend;

  @Inject
  ReplicationStatusServlet(
      Gson gson, ReplicationStatus replicationStatus, PermissionBackend permissionBackend) {
    this.gson = gson;
    this.replicationStatus = replicationStatus;
    this.permissionBackend = permissionBackend;
  }

  @Override
  protected void doGet(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
      throws ServletException, IOException {
    if (!permissionBackend.currentUser().testOrFalse(ADMINISTRATE_SERVER)) {
      setResponse(
          httpServletResponse,
          HttpServletResponse.SC_FORBIDDEN,
          String.format("%s permissions required. Operation not permitted", ADMINISTRATE_SERVER));
      return;
    }

    int limitResult =
        Optional.ofNullable(httpServletRequest.getParameter(LIMIT_RESULT_PARAMETER))
            .map(Integer::parseInt)
            .orElse(DEFAULT_LIMIT_RESULT_PARAMETER);

    setResponse(
        httpServletResponse,
        HttpServletResponse.SC_OK,
        gson.toJson(replicationStatus.getReplicationLags(limitResult)));
  }

  static void setResponse(HttpServletResponse httpResponse, int statusCode, String value)
      throws IOException {
    httpResponse.setContentType("application/json");
    httpResponse.setStatus(statusCode);
    PrintWriter writer = httpResponse.getWriter();
    writer.print(new String(RestApiServlet.JSON_MAGIC));
    writer.print(value);
  }
}
