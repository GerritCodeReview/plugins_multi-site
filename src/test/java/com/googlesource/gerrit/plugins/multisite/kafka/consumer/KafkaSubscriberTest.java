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

import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.ApprovalAttribute;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gson.Gson;
import com.googlesource.gerrit.plugins.multisite.KafkaConfiguration;
import com.googlesource.gerrit.plugins.multisite.KafkaConfiguration.Kafka;
import com.googlesource.gerrit.plugins.multisite.KafkaConfiguration.KafkaSubscriber;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.broker.GsonProvider;
import com.googlesource.gerrit.plugins.multisite.consumer.SubscriberMetrics;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import com.googlesource.gerrit.plugins.multisite.kafka.router.ForwardedEventRouter;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KafkaSubscriberTest {
  private static final Integer POOLING_INTERVAL = 10;

  @Mock private KafkaConfiguration kafkaConfig;
  @Mock private Kafka kafka;
  @Mock private KafkaSubscriber subscriber;
  @Mock private KafkaConsumerFactory consumerFactory;
  @Mock private ForwardedEventRouter eventRouter;
  @Mock private DynamicSet<DroppedEventListener> droppedEventListeners;
  @Mock private OneOffRequestContext oneOffCtx;
  @Mock private MessageLogger msgLog;
  @Mock private SubscriberMetrics subscriberMetrics;

  private Deserializer<byte[]> keyDeserializer;
  private Deserializer<SourceAwareEventWrapper> valueDeserializer;

  private Gson gson = new GsonProvider().get();
  private UUID instanceId;
  private MockConsumer<byte[], byte[]> consumer;
  private ExecutorService executor = Executors.newFixedThreadPool(1);

  private AbstractKafkaSubcriber objectUnderTest;

  @Before
  public void setup() {
    keyDeserializer = new ByteArrayDeserializer();
    valueDeserializer = new KafkaEventDeserializer(gson);
    consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
    instanceId = UUID.randomUUID();

    when(kafkaConfig.getKafka()).thenReturn(kafka);
    when(kafka.getTopic(EventFamily.CACHE_EVENT)).thenReturn("sample_topic");
    when(kafkaConfig.kafkaSubscriber()).thenReturn(subscriber);
    when(subscriber.getPollingInterval()).thenReturn(POOLING_INTERVAL);
    when(consumerFactory.create(keyDeserializer, instanceId)).thenReturn(consumer);

    objectUnderTest =
        new AbstractKafkaSubcriber(
            kafkaConfig,
            consumerFactory,
            keyDeserializer,
            valueDeserializer,
            eventRouter,
            droppedEventListeners,
            gson,
            instanceId,
            oneOffCtx,
            msgLog,
            subscriberMetrics) {

          @Override
          protected EventFamily getEventFamily() {
            return EventFamily.CACHE_EVENT;
          }
        };
  }

  @Test
  public void shouldIncrementSubscriberConsumedMessage() {
    String eventJson = createSampleJsonEvent(gson.toJson(createSampleEvent()));
    scheduleOnePoll(eventJson);
    objectUnderTest.run();
    verify(subscriberMetrics, only()).incrementSubscriberConsumedMessage();
  }

  @Test
  public void shouldIncrementSubscriberFailedToConsumeMessage() {
    String eventJson = createSampleJsonEvent("{}");
    scheduleOnePoll(eventJson);
    objectUnderTest.run();
    verify(subscriberMetrics, only()).incrementSubscriberFailedToConsumeMessage();
  }

  @Test
  public void shouldIncrementFailedToPollMessagesWhenExceptionFromConsumer() {
    consumer.setException(new KafkaException("Sample exception"));
    try {
      objectUnderTest.run();
    } catch (KafkaException e) {
      // expected
    }
    verify(subscriberMetrics, only()).incrementSubscriberFailedToPollMessages();
  }

  private void scheduleOnePoll(String eventJson) {
    AtomicBoolean polled = new AtomicBoolean(false);
    consumer.schedulePollTask(
        () -> {
          if (!polled.get()) {
            consumer.rebalance(Collections.singletonList(new TopicPartition("sample_topic", 0)));
            consumer.addRecord(
                new ConsumerRecord<>(
                    "sample_topic",
                    0,
                    0L,
                    EventFamily.CACHE_EVENT.name().getBytes(),
                    eventJson.getBytes()));
            consumer.seek(new TopicPartition("sample_topic", 0), 0L);
            polled.set(true);
          }
        });

    executor.execute(
        () -> {
          while (!polled.get()) {
            try {
              Thread.sleep(POOLING_INTERVAL);
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
          }
          objectUnderTest.shutdown();
        });
  }

  private String createSampleJsonEvent(String body) {
    UUID eventId = UUID.randomUUID();
    String eventType = "event-type";
    UUID sourceInstanceId = UUID.randomUUID();
    long eventCreatedOn = 10L;

    return String.format(
        "{ "
            + "\"header\": { \"eventId\": \"%s\", \"eventType\": \"%s\", \"sourceInstanceId\": \"%s\", \"eventCreatedOn\": %d },"
            + "\"body\": %s"
            + "}",
        eventId, eventType, sourceInstanceId, eventCreatedOn, body);
  }

  private Event createSampleEvent() {
    String accountName = "Foo Bar";
    String accountEmail = "foo@bar.com";
    String accountUsername = "foobar";

    String approvalDescription = "ApprovalDescription";
    String approvalValue = "+2";
    String oldApprovalValue = "+1";
    Long approvalGrantedOn = 123L;
    String commentDescription = "Patch Set 1: Code-Review+2";
    String projectName = "project";
    String refName = "refs/heads/master";
    String changeId = "Iabcd1234abcd1234abcd1234abcd1234abcd1234";
    Long eventCreatedOn = 123L;

    Change change =
        new Change(
            new Change.Key(changeId),
            new Change.Id(1),
            new Account.Id(1),
            new Branch.NameKey(projectName, refName),
            TimeUtil.nowTs());

    CommentAddedEvent event = new CommentAddedEvent(change);
    AccountAttribute accountAttribute = new AccountAttribute();
    accountAttribute.email = accountEmail;
    accountAttribute.name = accountName;
    accountAttribute.username = accountUsername;

    event.eventCreatedOn = eventCreatedOn;
    event.approvals =
        () -> {
          ApprovalAttribute approvalAttribute = new ApprovalAttribute();
          approvalAttribute.value = approvalValue;
          approvalAttribute.oldValue = oldApprovalValue;
          approvalAttribute.description = approvalDescription;
          approvalAttribute.by = accountAttribute;
          approvalAttribute.type = ChangeKind.REWORK.toString();
          approvalAttribute.grantedOn = approvalGrantedOn;

          return new ApprovalAttribute[] {approvalAttribute};
        };

    event.author = () -> accountAttribute;
    event.comment = commentDescription;

    return event;
  }
}
