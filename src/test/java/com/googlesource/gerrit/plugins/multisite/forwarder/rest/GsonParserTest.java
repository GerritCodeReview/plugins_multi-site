// Copyright (C) 2015 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.forwarder.rest;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.googlesource.gerrit.plugins.multisite.cache.Constants;
import com.googlesource.gerrit.plugins.multisite.forwarder.rest.GsonParser;

import org.junit.Test;

public class GsonParserTest {
  private static final Object EMPTY_JSON = "{}";

  @Test
  public void accountIDParse() {
    Account.Id accountId = new Account.Id(1);
    String json = GsonParser.toJson(Constants.ACCOUNTS, accountId);
    assertThat(accountId).isEqualTo(GsonParser.fromJson(Constants.ACCOUNTS, json));
  }

  @Test
  public void accountGroupIDParse() {
    AccountGroup.Id accountGroupId = new AccountGroup.Id(1);
    String json = GsonParser.toJson(Constants.GROUPS, accountGroupId);
    assertThat(accountGroupId).isEqualTo(GsonParser.fromJson(Constants.GROUPS, json));
  }

  @Test
  public void accountGroupUUIDParse() {
    AccountGroup.UUID accountGroupUuid = new AccountGroup.UUID("abc123");
    String json = GsonParser.toJson(Constants.GROUPS_BYINCLUDE, accountGroupUuid);
    assertThat(accountGroupUuid).isEqualTo(GsonParser.fromJson(Constants.GROUPS_BYINCLUDE, json));
  }

  @Test
  public void stringParse() {
    String key = "key";
    String json = GsonParser.toJson(Constants.PROJECTS, key);
    assertThat(key).isEqualTo(GsonParser.fromJson(Constants.PROJECTS, json));
  }

  @Test
  public void noKeyParse() {
    Object object = new Object();
    String json = GsonParser.toJson(Constants.PROJECT_LIST, object);
    assertThat(json).isEqualTo(EMPTY_JSON);
  }
}
