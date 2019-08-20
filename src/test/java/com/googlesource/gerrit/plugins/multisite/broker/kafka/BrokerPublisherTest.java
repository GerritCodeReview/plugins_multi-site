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

package com.googlesource.gerrit.plugins.multisite.broker.kafka;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.ApprovalAttribute;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerMetrics;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerPublisher;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerSession;
import com.googlesource.gerrit.plugins.multisite.broker.GsonProvider;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BrokerPublisherTest {

  @Mock private BrokerMetrics brokerMetrics;
  @Mock private BrokerSession session;
  @Mock private MessageLogger msgLog;
  private BrokerPublisher publisher;

  private Gson gson = new GsonProvider().get();

  private String accountName = "Foo Bar";
  private String accountEmail = "foo@bar.com";
  private String accountUsername = "foobar";
  private String approvalType = ChangeKind.REWORK.toString();

  private String approvalDescription = "ApprovalDescription";
  private String approvalValue = "+2";
  private String oldApprovalValue = "+1";
  private Long approvalGrantedOn = 123L;
  private String commentDescription = "Patch Set 1: Code-Review+2";
  private String projectName = "project";
  private String refName = "refs/heads/master";
  private String changeId = "Iabcd1234abcd1234abcd1234abcd1234abcd1234";
  private Long eventCreatedOn = 123L;

  @Before
  public void setUp() {
    publisher = new BrokerPublisher(session, gson, UUID.randomUUID(), msgLog, brokerMetrics);
  }

  @Test
  public void shouldSerializeCommentAddedEvent() {

    Event event = createSampleEvent();

    String expectedSerializedCommentEvent =
        "{\"author\": {\"name\": \""
            + accountName
            + "\",\"email\": \""
            + accountEmail
            + "\",\"username\": \""
            + accountUsername
            + "\"},\"approvals\": [{\"type\": \""
            + approvalType
            + "\",\"description\": \""
            + approvalDescription
            + "\",\"value\": \""
            + approvalValue
            + "\",\"oldValue\": \""
            + oldApprovalValue
            + "\",\"grantedOn\": "
            + approvalGrantedOn
            + ",\"by\": {\"name\": \""
            + accountName
            + "\",\"email\": \""
            + accountEmail
            + "\",\"username\": \""
            + accountUsername
            + "\"}}],\"comment\": \""
            + commentDescription
            + "\",\""
            + projectName
            + "\": {\"name\": \""
            + projectName
            + "\"},\"refName\": \""
            + refName
            + "\",\"changeKey\": {\"id\": \""
            + changeId
            + "\""
            + "  },\"type\": \"comment-added\",\"eventCreatedOn\": "
            + eventCreatedOn
            + "}";

    JsonObject expectedCommentEventJsonObject =
        gson.fromJson(expectedSerializedCommentEvent, JsonElement.class).getAsJsonObject();

    assertThat(publisher.eventToJson(event).equals(expectedCommentEventJsonObject)).isTrue();
  }

  @Test
  public void shouldIncrementBrokerMetricCounterWhenMessagePublished() {
    Event event = createSampleEvent();
    when(session.publishEventToTopic(any(), any())).thenReturn(true);
    publisher.publish(EventFamily.INDEX_EVENT.topic(), event);
    verify(brokerMetrics, only()).incrementBrokerPublishedMessage();
  }

  @Test
  public void shouldIncrementBrokerFailedMetricCounterWhenMessagePublished() {
    Event event = createSampleEvent();
    when(session.publishEventToTopic(any(), any())).thenReturn(false);

    publisher.publish(EventFamily.INDEX_EVENT.topic(), event);
    verify(brokerMetrics, only()).incrementBrokerFailedToPublishMessage();
  }

  @Test
  public void shouldLogEventPublishedMessageWhenPublishingSucceed() {
    Event event = createSampleEvent();
    when(session.publishEventToTopic(any(), any())).thenReturn(true);
    publisher.publish(EventFamily.INDEX_EVENT.topic(), event);
    verify(msgLog, only()).log(any(), any());
  }

  @Test
  public void shouldSkipEventPublishedLoggingWhenMessagePublishigFailed() {
    Event event = createSampleEvent();
    when(session.publishEventToTopic(any(), any())).thenReturn(false);

    publisher.publish(EventFamily.INDEX_EVENT.topic(), event);
    verify(msgLog, never()).log(any(), any());
  }

  private Event createSampleEvent() {
    final Change change =
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
