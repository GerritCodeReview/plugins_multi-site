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

package com.googlesource.gerrit.plugins.multisite.kafka.consumer;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.Executor;

@Singleton
public class MultiSiteKafkaConsumerRunner implements LifecycleListener {
  private KafkaSubcriber consumer;

  private final Executor executor;

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject
  public MultiSiteKafkaConsumerRunner(
      KafkaSubcriber consumer, @ConsumerExecutor Executor executor) {
    this.consumer = consumer;
    this.executor = executor;
  }

  @Override
  public void start() {
    // Read instance id

    final ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(KafkaSubcriber.class.getClassLoader());
      executor.execute(consumer);
    } finally {
      Thread.currentThread().setContextClassLoader(previousClassLoader);
    }
  }

  @Override
  public void stop() {
    if (consumer != null) {
      logger.atInfo().log("shutting down consumer");

      this.consumer.shutdown();
    }
  }
}
