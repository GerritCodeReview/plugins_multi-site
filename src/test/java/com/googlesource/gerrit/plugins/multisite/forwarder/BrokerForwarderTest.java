// Copyright (C) 2020 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.forwarder;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gerritforge.gerrit.eventbroker.EventsBrokerApiWrapper;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerMetrics;
import com.googlesource.gerrit.plugins.multisite.forwarder.broker.BrokerForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.MultiSiteEvent;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BrokerForwarderTest {
  private static final String HIGH_AVAILABILITY_PLUGIN = "/plugins/high-availability/";
  private static final String HIGH_AVAILABILITY_FORWARDED = "Forwarded-Index-Event";
  private static final String HIGH_AVAILABILITY_BATCH_FORWARDED = "Forwarded-BatchIndex-Event";
  private static final long TEST_TIMEOUT_SEC = 5L;

  @Mock private EventsBrokerApiWrapper brokerMock;

  private TestBrokerForwarder brokerForwarder;

  private Configuration cfg;

  private EventTopic testTopic;

  private String testTopicName;

  private TestEvent testEvent;

  private ExecutorService executor;
  @Mock private BrokerMetrics metrics;

  public class TestBrokerForwarder extends BrokerForwarder {

    TestBrokerForwarder() {
      super(brokerMock, cfg, metrics);
    }

    public void send(ForwarderTask task, EventTopic eventTopic, TestEvent testEvent) {
      super.send(task, eventTopic, testEvent);
    }
  }

  public static class TestEvent extends MultiSiteEvent {

    protected TestEvent() {
      super("test", "instance-id");
    }
  }

  @Before
  public void setup() {
    cfg = new Configuration(new Config(), new Config());
    testTopic = EventTopic.INDEX_TOPIC;
    testTopicName = testTopic.topic(cfg);
    testEvent = new TestEvent();
    brokerForwarder = new TestBrokerForwarder();
    executor = Executors.newSingleThreadExecutor();
  }

  @After
  public void teardown() {
    executor.shutdown();
  }

  @Test
  public void shouldSendEventToBrokerFromGenericSourceThread() {
    brokerForwarder.send(newForwarderTask(), testTopic, testEvent);
    verify(brokerMock).sendSync(eq(testTopicName), eq(testEvent));
  }

  @Test
  public void shouldSkipEventFromHighAvailabilityPluginThread() {
    brokerForwarder.send(newForwarderTask(HIGH_AVAILABILITY_PLUGIN), testTopic, testEvent);
    verifyNoInteractions(brokerMock);
  }

  @Test
  public void shouldSkipEventFromHighAvailabilityPluginForwardedThread() {
    brokerForwarder.send(newForwarderTask(HIGH_AVAILABILITY_FORWARDED), testTopic, testEvent);

    verifyNoInteractions(brokerMock);
  }

  @Test
  public void shouldSkipEventFromHighAvailabilityPluginBatchForwardedThread() {
    brokerForwarder.send(newForwarderTask(HIGH_AVAILABILITY_BATCH_FORWARDED), testTopic, testEvent);

    verifyNoInteractions(brokerMock);
  }

  @Test
  public void shouldWaitForTheMessagePublishingResult() {
    brokerForwarder.send(newForwarderTask(), testTopic, testEvent);

    verify(brokerMock).sendSync(testTopicName, testEvent);
  }

  @Test
  public void shouldIncrementBrokerPublishedMessageMetricOnSuccess() {
    when(brokerMock.sendSync(testTopicName, testEvent)).thenReturn(true);

    brokerForwarder.send(newForwarderTask(), testTopic, testEvent);

    verify(metrics).incrementBrokerPublishedMessage();
  }

  @Test
  public void shouldIncrementBrokerFailedToPublishMessageMetricOnFailure() {
    when(brokerMock.sendSync(testTopicName, testEvent)).thenReturn(false);

    brokerForwarder.send(newForwarderTask(), testTopic, testEvent);

    verify(metrics).incrementBrokerFailedToPublishMessage();
  }

  private ForwarderTask newForwarderTask(String threadName) {
    try {
      return executor
          .submit(
              () -> {
                Thread.currentThread().setName(threadName);
                return newForwarderTask();
              })
          .get(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private ForwarderTask newForwarderTask() {
    return new ForwarderTask() {

      @Override
      public void run() {}
    };
  }
}
