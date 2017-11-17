// Copyright (C) 2017 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.health;

import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;

import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class HealthServlet extends HttpServlet {
  private static final Logger log = LoggerFactory.getLogger(HealthServlet.class);
  private static final long serialVersionUID = -1L;

  private boolean healthy;

  HealthServlet() {
    this.healthy = true;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp) {
    this.healthy = true;
    rsp.setStatus(SC_NO_CONTENT);
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse rsp) {
    this.healthy = false;
    rsp.setStatus(SC_NO_CONTENT);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp) {
    if (healthy) {
      rsp.setStatus(SC_NO_CONTENT);
    } else {
      try {
        rsp.sendError(SC_SERVICE_UNAVAILABLE);
      } catch (IOException e) {
        log.error("Failed to send error response", e);
      }
    }
  }
}
