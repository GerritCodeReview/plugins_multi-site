// Copyright (C) 2023 The Android Open Source Project
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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerMetrics;
import com.googlesource.gerrit.plugins.multisite.consumer.SubscriberMetrics;

@Singleton
public class MultiSiteMetricsLifecycleListener implements LifecycleListener {

  private final BrokerMetrics brokerMetrics;
  private final SubscriberMetrics subscriberMetrics;

  @Inject
  public MultiSiteMetricsLifecycleListener(
      BrokerMetrics brokerMetrics, SubscriberMetrics subscriberMetrics) {

    this.brokerMetrics = brokerMetrics;
    this.subscriberMetrics = subscriberMetrics;
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    brokerMetrics.stop();
    subscriberMetrics.stop();
  }
}
