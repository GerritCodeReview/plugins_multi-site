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

import com.gerritforge.gerrit.eventbroker.SourceAwareEventWrapper;
import com.google.gerrit.extensions.systemstatus.ServerInformation;
import com.google.gerrit.server.util.PluginLogFile;
import com.google.gerrit.server.util.SystemLog;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.log4j.PatternLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Log4jMessageLogger extends PluginLogFile implements MessageLogger {
  private static final String LOG_NAME = "message_log";
  private final Logger msgLog;

  @Inject
  public Log4jMessageLogger(SystemLog systemLog, ServerInformation serverInfo) {
    super(systemLog, serverInfo, LOG_NAME, new PatternLayout("[%d{ISO8601}] [%t] %-5p : %m%n"));
    msgLog = LoggerFactory.getLogger(LOG_NAME);
  }

  @Override
  public void log(Direction direction, SourceAwareEventWrapper event) {
    msgLog.info("{} Header[{}] Body[{}]", direction, event.getHeader(), event.getBody());
  }
}
