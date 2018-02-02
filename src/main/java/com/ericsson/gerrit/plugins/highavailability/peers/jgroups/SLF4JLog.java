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
package com.ericsson.gerrit.plugins.highavailability.peers.jgroups;

import org.jgroups.logging.Log;
import org.slf4j.Logger;

/**
 * A {@link Log} implementation which forwards all calls to the JGroups specific logging interface
 * to an instance of a SLF4J {@link Logger}. Only {@link #setLevel(String)} and {@link #getLevel()}
 * have no counterparts in SLF4J and are implemented as NOPs. Instances of this class are created by
 * {@link SLF4JLogFactory}. See the documentation of {@link SLF4JLogFactory} on how to configure
 * JGroups to use this class.
 *
 * @author christian.halstrick@sap.com
 */
public class SLF4JLog implements Log {
  private Logger logger;

  public SLF4JLog(Logger log) {
    this.logger = log;
  }

  @Override
  public boolean isFatalEnabled() {
    return logger.isErrorEnabled();
  }

  @Override
  public boolean isErrorEnabled() {
    return logger.isErrorEnabled();
  }

  @Override
  public boolean isWarnEnabled() {
    return logger.isWarnEnabled();
  }

  @Override
  public boolean isInfoEnabled() {
    return logger.isInfoEnabled();
  }

  @Override
  public boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }

  @Override
  public boolean isTraceEnabled() {
    return logger.isTraceEnabled();
  }

  @Override
  public void fatal(String msg) {
    logger.error(msg);
  }

  @Override
  public void fatal(String msg, Object... args) {
    logger.error(convMsg(msg, args));
  }

  @Override
  public void fatal(String msg, Throwable throwable) {
    logger.error(msg, throwable);
  }

  @Override
  public void error(String msg) {
    logger.error(msg);
  }

  @Override
  public void error(String format, Object... args) {
    if (isErrorEnabled()) {
      logger.error(convMsg(format, args));
    }
  }

  @Override
  public void error(String msg, Throwable throwable) {
    logger.error(msg, throwable);
  }

  @Override
  public void warn(String msg) {
    logger.warn(msg);
  }

  @Override
  public void warn(String msg, Object... args) {
    if (isWarnEnabled()) {
      logger.warn(convMsg(msg, args));
    }
  }

  @Override
  public void warn(String msg, Throwable throwable) {
    logger.warn(msg, throwable);
  }

  @Override
  public void info(String msg) {
    logger.info(msg);
  }

  @Override
  public void info(String msg, Object... args) {
    if (isInfoEnabled()) {
      logger.info(convMsg(msg, args));
    }
  }

  @Override
  public void debug(String msg) {
    logger.info(msg);
  }

  @Override
  public void debug(String msg, Object... args) {
    if (isDebugEnabled()) {
      logger.debug(convMsg(msg, args));
    }
  }

  @Override
  public void debug(String msg, Throwable throwable) {
    logger.debug(msg, throwable);
  }

  @Override
  public void trace(Object msg) {
    logger.trace(msg.toString());
  }

  @Override
  public void trace(String msg) {
    logger.trace(msg);
  }

  @Override
  public void trace(String msg, Object... args) {
    if (isTraceEnabled()) {
      logger.trace(convMsg(msg, args));
    }
  }

  @Override
  public void trace(String msg, Throwable throwable) {
    logger.trace(msg, throwable);
  }

  private static String convMsg(String msg, Object... args) {
    return String.format(msg, args);
  }

  @Override
  public void setLevel(String level) {}

  @Override
  public String getLevel() {
    return "";
  }
}
