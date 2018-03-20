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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedProjectListUpdateHandler;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
class ProjectListApiServlet extends AbstractRestApiServlet {
  private static final long serialVersionUID = -1L;

  private final ForwardedProjectListUpdateHandler forwardedProjectListUpdateHandler;

  @Inject
  ProjectListApiServlet(ForwardedProjectListUpdateHandler forwardedProjectListUpdateHandler) {
    this.forwardedProjectListUpdateHandler = forwardedProjectListUpdateHandler;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp) {
    process(req, rsp, false);
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse rsp) {
    process(req, rsp, true);
  }

  private void process(HttpServletRequest req, HttpServletResponse rsp, boolean delete) {
    setHeaders(rsp);
    String path = req.getPathInfo();
    String projectName = path.substring(path.lastIndexOf('/') + 1);
    forwardedProjectListUpdateHandler.update(projectName, delete);
    rsp.setStatus(SC_NO_CONTENT);
  }
}
