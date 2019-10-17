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

package com.googlesource.gerrit.plugins.multisite.forwarder;

import com.google.common.base.Strings;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.events.EventGson;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.cache.Constants;

public final class GsonParser {
  private final Gson gson;

  @Inject
  public GsonParser(@EventGson Gson gson) {
    this.gson = gson;
  }

  public Object fromJson(String cacheName, String jsonString) {
    JsonElement json = gson.fromJson(Strings.nullToEmpty(jsonString), JsonElement.class);
    Object key;
    // Need to add a case for 'adv_bases'
    switch (cacheName) {
      case Constants.ACCOUNTS:
        key = Account.id(json.getAsJsonObject().get("id").getAsInt());
        break;
      case Constants.GROUPS:
        key = AccountGroup.id(json.getAsJsonObject().get("id").getAsInt());
        break;
      case Constants.GROUPS_BYINCLUDE:
      case Constants.GROUPS_MEMBERS:
        key = AccountGroup.uuid(json.getAsJsonObject().get("uuid").getAsString());
        break;
      case Constants.PROJECT_LIST:
        key = gson.fromJson(json, Object.class);
        break;
      default:
        try {
          key = gson.fromJson(json, String.class);
        } catch (Exception e) {
          key = gson.fromJson(json, Object.class);
        }
    }
    return key;
  }
}
