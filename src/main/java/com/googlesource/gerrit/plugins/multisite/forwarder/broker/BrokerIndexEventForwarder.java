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

package com.googlesource.gerrit.plugins.multisite.forwarder.broker;

import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerPublisher;
import com.googlesource.gerrit.plugins.multisite.forwarder.IndexEventForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.AccountIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.GroupIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectIndexEvent;

public class BrokerIndexEventForwarder implements IndexEventForwarder {
  private final BrokerPublisher publisher;

  @Inject
  BrokerIndexEventForwarder(BrokerPublisher publisher) {
    this.publisher = publisher;
  }

  @Override
  public boolean indexAccount(AccountIndexEvent event) {
    return publisher.publishEvent(EventFamily.INDEX_EVENT, event);
  }

  @Override
  public boolean indexChange(ChangeIndexEvent event) {
    return publisher.publishEvent(EventFamily.INDEX_EVENT, event);
  }

  @Override
  public boolean indexGroup(GroupIndexEvent event) {
    return publisher.publishEvent(EventFamily.INDEX_EVENT, event);
  }

  @Override
  public boolean deleteChangeFromIndex(ChangeIndexEvent event) {
    return publisher.publishEvent(EventFamily.INDEX_EVENT, event);
  }

  @Override
  public boolean indexProject(ProjectIndexEvent event) {
    return publisher.publishEvent(EventFamily.INDEX_EVENT, event);
  }
}
