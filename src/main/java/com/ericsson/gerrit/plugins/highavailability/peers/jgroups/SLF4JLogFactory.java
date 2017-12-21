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

import org.jgroups.logging.CustomLogFactory;
import org.jgroups.logging.Log;
import org.slf4j.LoggerFactory;

/**
 * A custom log factory which takes care to provide instances of {@link SLF4JLog}. If this factory
 * is used jgroups will use the SLF4J API to emit logging and tracing. This can be helpful when
 * gerrit is running in a runtime environment which supports SLF4J logging.
 *
 * <p>To configure jgroups to use this log factory make sure the following system property is set
 * when gerrit is started:
 *
 * <p>{@code
 * -Djgroups.logging.log_factory_class=com.ericsson.gerrit.plugins.highavailability.peers.jgroups.SLF4JLogFactory}
 */
public class SLF4JLogFactory implements CustomLogFactory {

  @Override
  public Log getLog(Class clazz) {
    return new SLF4JLog(LoggerFactory.getLogger(clazz));
  }

  @Override
  public Log getLog(String category) {
    return new SLF4JLog(LoggerFactory.getLogger(category));
  }
}
