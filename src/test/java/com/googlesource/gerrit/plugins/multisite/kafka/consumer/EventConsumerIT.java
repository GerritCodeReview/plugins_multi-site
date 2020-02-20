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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.data.PatchSetAttribute;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.PatchSetCreatedEvent;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.Module;
import com.googlesource.gerrit.plugins.multisite.NoteDbStatus;
import com.googlesource.gerrit.plugins.multisite.PluginModule;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerApi;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerApiWrapper;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerGson;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerModule;
import com.googlesource.gerrit.plugins.multisite.consumer.DroppedEventListener;
import com.googlesource.gerrit.plugins.multisite.consumer.SourceAwareEventWrapper;
import com.googlesource.gerrit.plugins.multisite.consumer.SubscriberModule;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import com.googlesource.gerrit.plugins.multisite.kafka.KafkaBrokerModule;
import com.googlesource.gerrit.plugins.multisite.kafka.KafkaConfiguration;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.ZkValidationModule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Test;
import org.testcontainers.containers.KafkaContainer;

@NoHttpd
@UseLocalDisk
public class EventConsumerIT extends AbstractDaemonTest {
  public static final String GERRIT_CONFIG_KEY = "gerrit.installModule";
  public static final String GERRIT_CONFIG_VALUE =
      "com.googlesource.gerrit.plugins.multisite.kafka.consumer.EventConsumerIT$KafkaTestContainerModule";
  private static final int QUEUE_POLL_TIMEOUT_MSECS = 10000;

  static {
    System.setProperty("gerrit.notedb", "ON");
  }

  public static class KafkaTestContainerModule extends LifecycleModule {

    public static class KafkaStopAtShutdown implements LifecycleListener {
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

    public static class TestBrokerModule extends BrokerModule {
      @Override
      protected void configure() {
        DynamicItem.itemOf(binder(), BrokerApi.class);
        bind(BrokerApiWrapper.class).in(Scopes.SINGLETON);

        install(new SubscriberModule());
      }
    }

    private final FileBasedConfig config;
    private final Module multiSiteModule;
    private final PluginModule pluginModule;

    @Inject
    public KafkaTestContainerModule(SitePaths sitePaths, NoteDbStatus noteDb) throws IOException {
      this.config =
          new FileBasedConfig(
              sitePaths.etc_dir.resolve(Configuration.MULTI_SITE_CONFIG).toFile(), FS.DETECTED);
      config.setBoolean("kafka", "publisher", "enabled", true);
      config.setBoolean("kafka", "subscriber", "enabled", true);
      config.setBoolean("ref-database", null, "enabled", false);
      config.save();

      Configuration multiSiteConfig = new Configuration(config, new Config());
      this.multiSiteModule = new Module(multiSiteConfig, noteDb, new TestBrokerModule(), true);
      this.pluginModule =
          new PluginModule(
              multiSiteConfig,
              new ZkValidationModule(multiSiteConfig),
              new KafkaBrokerModule(new KafkaConfiguration(multiSiteConfig)));
    }

    @Override
    protected void configure() {
      try {
        final KafkaContainer kafka = startAndConfigureKafkaConnection();

        listener().toInstance(new KafkaStopAtShutdown(kafka));

        install(multiSiteModule);
        install(pluginModule);

      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    private KafkaContainer startAndConfigureKafkaConnection() throws IOException {
      KafkaContainer kafkaContainer = new KafkaContainer();
      kafkaContainer.start();

      config.setString("kafka", null, "bootstrapServers", kafkaContainer.getBootstrapServers());
      config.save();

      return kafkaContainer;
    }
  }

  @Test
  @GerritConfig(name = GERRIT_CONFIG_KEY, value = GERRIT_CONFIG_VALUE)
  public void createChangeShouldPropagateChangeIndexAndRefUpdateStreamEvent() throws Exception {
    LinkedBlockingQueue<SourceAwareEventWrapper> droppedEventsQueue = captureDroppedEvents();
    drainQueue(droppedEventsQueue);

    ChangeData change = createChange().getChange();
    String project = change.project().get();
    int changeNum = change.getId().get();
    String changeNotesRef = change.notes().getRefName();
    int patchsetNum = change.currentPatchSet().getPatchSetId();
    String patchsetRevision = change.currentPatchSet().getRevision().get();
    String patchsetRef = change.currentPatchSet().getRefName();

    Map<String, List<Event>> eventsByType = receiveEventsByType(droppedEventsQueue);
    assertThat(eventsByType).isNotEmpty();

    assertThat(eventsByType.get("change-index"))
        .containsExactly(createChangeIndexEvent(project, changeNum, getParentCommit(change)));

    assertThat(
            eventsByType.get("ref-updated").stream()
                .map(e -> ((RefUpdatedEvent) e).getRefName())
                .collect(toSet()))
        .containsAllOf(changeNotesRef, patchsetRef); // 'refs/sequences/changes'
    // not always updated thus
    // not checked

    List<Event> patchSetCreatedEvents = eventsByType.get("patchset-created");
    assertThat(patchSetCreatedEvents).hasSize(1);
    assertPatchSetAttributes(
        (PatchSetCreatedEvent) patchSetCreatedEvents.get(0),
        patchsetNum,
        patchsetRevision,
        patchsetRef);
  }

  private void assertPatchSetAttributes(
      PatchSetCreatedEvent patchSetCreated,
      int patchsetNum,
      String patchsetRevision,
      String patchsetRef) {
    PatchSetAttribute patchSetAttribute = patchSetCreated.patchSet.get();
    assertThat(patchSetAttribute.number).isEqualTo(patchsetNum);
    assertThat(patchSetAttribute.revision).isEqualTo(patchsetRevision);
    assertThat(patchSetAttribute.ref).isEqualTo(patchsetRef);
  }

  @Test
  @GerritConfig(name = GERRIT_CONFIG_KEY, value = GERRIT_CONFIG_VALUE)
  public void reviewChangeShouldPropagateChangeIndexAndCommentAdded() throws Exception {
    LinkedBlockingQueue<SourceAwareEventWrapper> droppedEventsQueue = captureDroppedEvents();
    ChangeData change = createChange().getChange();
    String project = change.project().get();
    int changeNum = change.getId().get();
    drainQueue(droppedEventsQueue);

    ReviewInput in = ReviewInput.recommend();
    in.message = "LGTM";
    gApi.changes().id(changeNum).revision("current").review(in);

    Map<String, List<Event>> eventsByType = receiveEventsByType(droppedEventsQueue);

    assertThat(eventsByType).isNotEmpty();

    assertThat(eventsByType.get("change-index"))
        .containsExactly(createChangeIndexEvent(project, changeNum, getParentCommit(change)));

    List<Event> commentAddedEvents = eventsByType.get("comment-added");
    assertThat(commentAddedEvents).hasSize(1);
    assertThat(((CommentAddedEvent) commentAddedEvents.get(0)).comment)
        .isEqualTo("Patch Set 1: Code-Review+1\n\n" + in.message);
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
    server
        .getTestInjector()
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

  private Map<String, List<Event>> receiveEventsByType(
      LinkedBlockingQueue<SourceAwareEventWrapper> queue) throws InterruptedException {
    return drainQueue(queue).stream()
        .sorted(Comparator.comparing(e -> e.type))
        .collect(Collectors.groupingBy(e -> e.type));
  }

  private List<Event> drainQueue(LinkedBlockingQueue<SourceAwareEventWrapper> queue)
      throws InterruptedException {
    Gson gson = server.getTestInjector().getInstance(Key.get(Gson.class, BrokerGson.class));
    SourceAwareEventWrapper event;
    List<Event> eventsList = new ArrayList<>();
    while ((event = queue.poll(QUEUE_POLL_TIMEOUT_MSECS, TimeUnit.MILLISECONDS)) != null) {
      eventsList.add(event.getEventBody(gson));
    }
    return eventsList;
  }
}
