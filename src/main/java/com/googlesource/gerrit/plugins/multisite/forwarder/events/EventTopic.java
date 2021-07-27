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

import com.googlesource.gerrit.plugins.multisite.Configuration;

public enum EventTopic {
  INDEX_TOPIC("GERRIT.EVENT.INDEX", "indexEvent"),
  BATCH_INDEX_TOPIC("GERRIT.EVENT.BATCH.INDEX", "batchIndexEvent"),
  CACHE_TOPIC("GERRIT.EVENT.CACHE", "cacheEvent"),
  PROJECT_LIST_TOPIC("GERRIT.EVENT.PROJECT.LIST", "projectListEvent"),
  /**
   * the STREAM_EVENT_TOPICS and the GERRIT_TOPIC are both used publish stream events. It might not
   * be immediately intuitive why there are _two_ streams for this, rather than one. These are some
   * gotchas regarding this:
   *
   * <ul>
   *   <li>A subset of stream events is published to the STREAM_EVENT_TOPIC (i.e. only global
   *       project events). This is done by the EventHandler.
   *   <li>All, unfiltered gerrit events are published to a topic called `gerrit`. This is done by
   *       the StreamEventPublisher provided by the events-broker library.
   * </ul>
   *
   * The `gerrit` topic exists for historical reasons, since multi-site events had to be wrapped
   * into EventMessage and streamed separately, in order to provide a header containing the
   * instance-id that produced that message.
   *
   * <p>This has now been fixed as part of <a
   * href="https://bugs.chromium.org/p/gerrit/issues/detail?id=14823">Issue 14823</a>, so that the
   * `gerrit` stream, conceptually would not be needed anymore.
   *
   * <p>However, before the `gerrit` stream can be effectively removed, the filtering of the events
   * needs to happen on the receiving side, as explained in <a
   * href="https://bugs.chromium.org/p/gerrit/issues/detail?id=14835">Issue 14835</a>.
   *
   * <p>This would allow to *all* events to be streamed to the STREAM_EVENT_TOPICS.
   */
  STREAM_EVENT_TOPIC("GERRIT.EVENT.STREAM", "streamEvent"),
  GERRIT_TOPIC("gerrit", "gerritEvents");

  private final String topic;
  private final String aliasKey;

  private EventTopic(String topic, String aliasKey) {
    this.topic = topic;
    this.aliasKey = aliasKey;
  }

  public String topic(Configuration config) {
    return config.broker().getTopic(topicAliasKey(), topic);
  }

  public String topicAliasKey() {
    return aliasKey + "Topic";
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
