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
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.LogThreshold;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.data.PatchSetAttribute;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.PatchSetCreatedEvent;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.Module;
import com.googlesource.gerrit.plugins.multisite.broker.GsonProvider;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;
import org.testcontainers.containers.KafkaContainer;

@NoHttpd
@LogThreshold(level = "INFO")
@Sandboxed
@TestPlugin(
    name = "multi-site",
    sysModule =
        "com.googlesource.gerrit.plugins.multisite.kafka.consumer.EventConsumerIT$KafkaTestContainerModule")
public class EventConsumerIT extends LightweightPluginDaemonTest {
  private static final int QUEUE_POLL_TIMEOUT_MSECS = 30000;

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
      install(new Module(multiSiteConfig));

      listener().toInstance(new KafkaStopAtShutdown(kafka));
    }
  }

  @Test
  public void createChangeShouldPropagateChangeIndexAndRefUpdateStreamEvent() throws Exception {
    LinkedBlockingQueue<SourceAwareEventWrapper> droppedEventsQueue = captureDroppedEvents();
    drainQueue(droppedEventsQueue);

    PushOneCommit.Result r = createChange();

    List<Event> createdChangeEvents = receiveFromQueue(droppedEventsQueue, 5);
    assertThat(createdChangeEvents).hasSize(5);

    ChangeData change = r.getChange();
    assertThat(
            createdChangeEvents
                .stream()
                .filter(e -> e.type.equals("change-index"))
                .collect(toSet()))
        .containsExactlyElementsIn(
            ImmutableList.of(
                createChangeIndexEvent(
                    change.project().get(), change.getId().get(), getParentCommit(change))));

    assertThat(
            createdChangeEvents
                .stream()
                .filter(e -> e.type.equals("ref-updated"))
                .map(RefUpdatedEvent.class::cast)
                .map(e -> e.getRefName())
                .collect(toSet()))
        .containsExactlyElementsIn(
            ImmutableList.of(
                "refs/sequences/changes",
                change.currentPatchSet().getRefName(),
                RefNames.changeMetaRef(change.getId())));

    PatchSetCreatedEvent patchSetCreated =
        createdChangeEvents
            .stream()
            .filter(e -> e.type.equals("patchset-created"))
            .map(PatchSetCreatedEvent.class::cast)
            .findFirst()
            .get();
    PatchSetAttribute patchSetAttribute = patchSetCreated.patchSet.get();
    PatchSet currentPatchSet = change.currentPatchSet();
    assertThat(patchSetAttribute.number).isEqualTo(currentPatchSet.getPatchSetId());
    assertThat(patchSetAttribute.revision).isEqualTo(currentPatchSet.getRevision().get());
    assertThat(patchSetAttribute.ref).isEqualTo(currentPatchSet.getRefName());
  }

  @Test
  public void reviewChangeShouldPropagateChangeIndexAndCommentAdded() throws Exception {
    LinkedBlockingQueue<SourceAwareEventWrapper> droppedEventsQueue = captureDroppedEvents();
    PushOneCommit.Result r = createChange();
    drainQueue(droppedEventsQueue);

    ReviewInput in = ReviewInput.recommend();
    in.message = "LGTM";
    gApi.changes().id(r.getChangeId()).revision("current").review(in);

    List<Event> createdChangeEvents = receiveFromQueue(droppedEventsQueue, 3);
    assertThat(createdChangeEvents).hasSize(3);

    ChangeData change = r.getChange();
    assertThat(
            createdChangeEvents
                .stream()
                .filter(e -> e.type.equals("change-index"))
                .collect(toSet()))
        .containsExactlyElementsIn(
            ImmutableList.of(
                createChangeIndexEvent(
                    change.project().get(), change.getId().get(), getParentCommit(change))));

    RefUpdatedEvent refUpdated =
        createdChangeEvents
            .stream()
            .filter(e -> e.type.equals("ref-updated"))
            .map(RefUpdatedEvent.class::cast)
            .findFirst()
            .get();

    RevCommit commit;
    RevCommit parent;
    String refName = RefNames.changeMetaRef(change.getId());
    try (Repository repo = repoManager.openRepository(change.project());
        RevWalk walk = new RevWalk(repo)) {
      ObjectId objectId = repo.exactRef(refName).getObjectId();
      commit = walk.parseCommit(objectId);
      parent = commit.getParent(0);
    }
    assertRefUpdatedEvents(refUpdated, refName, parent, commit);

    CommentAddedEvent commentAdded =
        createdChangeEvents
            .stream()
            .filter(e -> e.type.equals("comment-added"))
            .map(CommentAddedEvent.class::cast)
            .findFirst()
            .get();
    assertThat(commentAdded.comment).isEqualTo("Patch Set 1: Code-Review+1\n\n" + in.message);
  }

  private String getParentCommit(ChangeData change) throws Exception {
    RevCommit parent;
    try (Repository repo = repoManager.openRepository(change.project());
        RevWalk walk = new RevWalk(repo)) {
      RevCommit commit =
          walk.parseCommit(ObjectId.fromString(change.currentPatchSet().getRevision().get()));
      parent = commit.getParent(0);
    }
    return parent.getId().name();
  }

  private void assertRefUpdatedEvents(
      RefUpdatedEvent event,
      String expectedRefName,
      RevCommit expectedParent,
      RevCommit expectedCommit)
      throws Exception {
    RefUpdateAttribute actual = event.refUpdate.get();
    assertThat(actual.refName).isEqualTo(expectedRefName);
    assertThat(actual.oldRev).isEqualTo(expectedParent.name());
    assertThat(actual.newRev).isEqualTo(expectedCommit.name());
  }

  private ChangeIndexEvent createChangeIndexEvent(
      String projectName, int changeId, String targetSha1) {
    ChangeIndexEvent event = new ChangeIndexEvent(projectName, changeId, false);
    event.targetSha = targetSha1;
    return event;
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

  private List<Event> receiveFromQueue(
      LinkedBlockingQueue<SourceAwareEventWrapper> queue, int numEvents)
      throws InterruptedException {
    GsonProvider gsonProvider = plugin.getSysInjector().getInstance(Key.get(GsonProvider.class));
    List<Event> eventsList = new ArrayList<>();

    for (int i = 0; i < numEvents; i++) {
      SourceAwareEventWrapper event = queue.poll(QUEUE_POLL_TIMEOUT_MSECS, TimeUnit.MILLISECONDS);
      if (event != null) {
        eventsList.add(event.getEventBody(gsonProvider));
      }
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
