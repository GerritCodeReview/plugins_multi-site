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

package com.googlesource.gerrit.plugins.multisite.forwarder.events;

import com.google.common.base.CaseFormat;

public enum EventTopic {
  INDEX_TOPIC("GERRIT.EVENT.INDEX"),
  CACHE_TOPIC("GERRIT.EVENT.CACHE"),
  PROJECT_LIST_TOPIC("GERRIT.EVENT.PROJECT.LIST"),
  STREAM_EVENT_TOPIC("GERRIT.EVENT.STREAM");

  private final String topic;

  private EventTopic(String topic) {
    this.topic = topic;
  }

  public String lowerCamelName() {
    return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name());
  }

  public String topic() {
    return topic;
  }

  public static EventTopic of(String topicString) {
    EventTopic[] topics = EventTopic.values();
    for (EventTopic topic : topics) {
      if (topic.topic.equals(topicString)) {
        return topic;
      }
    }
    return null;
  }
}
