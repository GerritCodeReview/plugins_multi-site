// Copyright (C) 2019 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite;

import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.util.SystemLog;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.log4j.PatternLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Log4jProjectVersionLogger extends LibModuleLogFile implements ProjectVersionLogger {
  private static final String LOG_NAME = "project_version_log";
  private final Logger verLog;

  @Inject
  public Log4jProjectVersionLogger(SystemLog systemLog) {
    super(systemLog, LOG_NAME, new PatternLayout("[%d{ISO8601}] [%t] %-5p : %m%n"));
    this.verLog = LoggerFactory.getLogger(LOG_NAME);
  }

  @Override
  public void log(NameKey projectName, long currentVersion, long replicationLag) {
    if (replicationLag > 0) {
      verLog.warn(
          "{ \"project\":\"{}\", \"version\":{}, \"lag\":{} }",
          projectName,
          currentVersion,
          replicationLag);
    } else {
      verLog.info("{ \"project\":\"{}\", \"version\":{} }", projectName, currentVersion);
    }
  }
}
