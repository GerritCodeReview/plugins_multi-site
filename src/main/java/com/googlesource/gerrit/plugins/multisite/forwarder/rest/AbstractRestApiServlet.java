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

package com.googlesource.gerrit.plugins.multisite.forwarder.rest;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractRestApiServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  protected final Logger log = LoggerFactory.getLogger(getClass());

  protected static void setHeaders(HttpServletResponse rsp) {
    rsp.setContentType("text/plain");
    rsp.setCharacterEncoding(UTF_8.name());
  }

  protected void sendError(HttpServletResponse rsp, int statusCode, String message) {
    try {
      rsp.sendError(statusCode, message);
    } catch (IOException e) {
      log.error("Failed to send error messsage: {}", e.getMessage(), e);
    }
  }
}
