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
import org.apache.log4j.AsyncAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public abstract class LibModuleLogFile {

  public LibModuleLogFile(SystemLog systemLog, String logName, Layout layout) {
    Logger logger = LogManager.getLogger(logName);
    if (logger.getAppender(logName) == null) {
      AsyncAppender asyncAppender = systemLog.createAsyncAppender(logName, layout, true, true);
      logger.addAppender(asyncAppender);
      logger.setAdditivity(false);
    }
  }
}
