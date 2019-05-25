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

import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.ApprovalAttribute;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.googlesource.gerrit.plugins.multisite.DisabledMessageLogger;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerPublisher;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerSession;
import com.googlesource.gerrit.plugins.multisite.broker.GsonProvider;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

public class BrokerPublisherTest {
  private BrokerPublisher publisher;
  private MessageLogger NO_MSG_LOG = new DisabledMessageLogger();
  private Gson gson = new GsonProvider().get();

  @Before
  public void setUp() {
    publisher = new BrokerPublisher(new TestBrokerSession(), gson, UUID.randomUUID(), NO_MSG_LOG);
  }

  @Test
  public void shouldSerializeCommentAddedEvent() {

    final String accountName = "Foo Bar";
    final String accountEmail = "foo@bar.com";
    final String accountUsername = "foobar";
    final String approvalType = ChangeKind.REWORK.toString();

    final String approvalDescription = "ApprovalDescription";
    final String approvalValue = "+2";
    final String oldApprovalValue = "+1";
    final Long approvalGrantedOn = 123L;
    final String commentDescription = "Patch Set 1: Code-Review+2";
    final String projectName = "project";
    final String refName = "refs/heads/master";
    final String changeId = "Iabcd1234abcd1234abcd1234abcd1234abcd1234";
    final Long eventCreatedOn = 123L;

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

  private static class TestBrokerSession implements BrokerSession {

    @Override
    public boolean isOpen() {
      return false;
    }

    @Override
    public void connect() {}

    @Override
    public void disconnect() {}

    @Override
    public boolean publishEvent(EventFamily eventFamily, String payload) {
      return false;
    }
  }
}
