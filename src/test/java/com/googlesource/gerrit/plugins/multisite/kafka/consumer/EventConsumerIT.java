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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.LogThreshold;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.Module;
import com.googlesource.gerrit.plugins.multisite.NoteDbStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.containers.KafkaContainer;

@NoHttpd
@LogThreshold(level = "INFO")
@TestPlugin(
    name = "multi-site",
    sysModule =
        "com.googlesource.gerrit.plugins.multisite.kafka.consumer.EventConsumerIT$KafkaTestContainerModule")
public class EventConsumerIT extends LightweightPluginDaemonTest {
  private static final int QUEUE_POLL_TIMEOUT_MSECS = 10000;

  static {
    System.setProperty("gerrit.notedb", "ON");
  }

  public static class KafkaTestContainerModule extends LifecycleModule {

    public class KafkaStopAtShutdown implements LifecycleListener {
      private final KafkaContainer kafka;

      public KafkaStopAtShutdown(KafkaContainer kafka) {
        this.kafka = kafka;
      }

      @Override
      public void stop() {
        kafka.stop();
      }

      @Override
      public void start() {
        // Do nothing
      }
    }

    private final NoteDbStatus noteDb;

    @Inject
    public KafkaTestContainerModule(NoteDbStatus noteDb) {
      this.noteDb = noteDb;
    }

    @Override
    protected void configure() {
      final KafkaContainer kafka = new KafkaContainer();
      kafka.start();

      Config config = new Config();
      config.setString("kafka", null, "bootstrapServers", kafka.getBootstrapServers());
      config.setBoolean("kafka", "publisher", "enabled", true);
      config.setBoolean("kafka", "subscriber", "enabled", true);
      Configuration multiSiteConfig = new Configuration(config);
      bind(Configuration.class).toInstance(multiSiteConfig);
      install(new Module(multiSiteConfig, noteDb));

      listener().toInstance(new KafkaStopAtShutdown(kafka));
    }
  }

  @Override
  @Before
  public void setUpTestPlugin() throws Exception {
    super.setUpTestPlugin();

    if (!notesMigration.commitChangeWrites()) {
      throw new IllegalStateException("NoteDb is mandatory for running the multi-site plugin");
    }
  }

  @Test
  public void createChangeShouldPropagateChangeIndexAndRefUpdateStreamEvent() throws Exception {
    LinkedBlockingQueue<SourceAwareEventWrapper> droppedEventsQueue = captureDroppedEvents();
    drainQueue(droppedEventsQueue);

    createChange();
    List<String> createdChangeEvents = receiveFromQueue(droppedEventsQueue);

    assertThat(createdChangeEvents).contains("change-index");
    assertThat(createdChangeEvents).contains("ref-updated");
    assertThat(createdChangeEvents).contains("patchset-created");
  }

  @Test
  public void reviewChangeShouldPropagateChangeIndexAndCommentAdded() throws Exception {
    LinkedBlockingQueue<SourceAwareEventWrapper> droppedEventsQueue = captureDroppedEvents();
    PushOneCommit.Result r = createChange();
    drainQueue(droppedEventsQueue);

    ReviewInput in = ReviewInput.recommend();
    in.message = "LGTM";
    gApi.changes().id(r.getChangeId()).revision("current").review(in);

    List<String> createdChangeEvents = receiveFromQueue(droppedEventsQueue);

    assertThat(createdChangeEvents).contains("change-index");
    assertThat(createdChangeEvents).contains("comment-added");
  }

  private LinkedBlockingQueue<SourceAwareEventWrapper> captureDroppedEvents() throws Exception {
    LinkedBlockingQueue<SourceAwareEventWrapper> droppedEvents = new LinkedBlockingQueue<>();

    TypeLiteral<DynamicSet<DroppedEventListener>> type =
        new TypeLiteral<DynamicSet<DroppedEventListener>>() {};
    plugin
        .getSysInjector()
        .getInstance(Key.get(type))
        .add(
            "multi-site",
            new DroppedEventListener() {
              @Override
              public void onEventDropped(SourceAwareEventWrapper event) {
                droppedEvents.offer(event);
              }
            });
    return droppedEvents;
  }

  private List<String> receiveFromQueue(LinkedBlockingQueue<SourceAwareEventWrapper> queue)
      throws InterruptedException {
    List<String> eventsList = new ArrayList<>();
    SourceAwareEventWrapper event;
    while ((event = queue.poll(QUEUE_POLL_TIMEOUT_MSECS, TimeUnit.MILLISECONDS)) != null) {
      eventsList.add(event.getHeader().getEventType());
    }
    return eventsList;
  }

  private void drainQueue(LinkedBlockingQueue<SourceAwareEventWrapper> queue)
      throws InterruptedException {
    while (queue.poll(QUEUE_POLL_TIMEOUT_MSECS, TimeUnit.MILLISECONDS) != null) {
      // Just consume the event
    }
  }
}
