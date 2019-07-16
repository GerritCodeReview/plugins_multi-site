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

import com.google.gerrit.server.util.SystemLog;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.log4j.PatternLayout;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Log4jSharedRefLogger extends LibModuleLogFile implements SharedRefLogger {
  private static final String LOG_NAME = "sharedref_log";
  private final Logger sharedRefDBLog;

  @Inject
  public Log4jSharedRefLogger(SystemLog systemLog) {
    super(systemLog, LOG_NAME, new PatternLayout("[%d{ISO8601}] [%t] %-5p : %m%n"));
    sharedRefDBLog = LoggerFactory.getLogger(LOG_NAME);
  }

  @Override
  public void logRefUpdate(String project, Ref currRef, ObjectId newRefValue) {
    sharedRefDBLog.info(
        "project:{}|ref:{}|oldId:{}|newId:{}",
        project,
        currRef.getName(),
        currRef.getObjectId().getName(),
        newRefValue.getName());
  }

  @Override
  public void logProjectDelete(String project) {
    sharedRefDBLog.info("project:{}|DELETED", project);
  }
}
