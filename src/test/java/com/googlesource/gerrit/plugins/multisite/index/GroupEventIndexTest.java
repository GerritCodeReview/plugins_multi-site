// Copyright (C) 2021 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.index;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.server.events.EventGsonProvider;
import com.google.gson.Gson;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.GroupIndexEvent;
import java.util.UUID;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class GroupEventIndexTest {
  private static final String INSTANCE_ID = "instance-id";
  private static final Gson gson = new EventGsonProvider().get();

  @Test
  public void groupEventIndexRoundTripWithSha1() {
    String aGroupUUID = UUID.randomUUID().toString();
    ObjectId anObjectId = ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    GroupIndexEvent original = new GroupIndexEvent(aGroupUUID, anObjectId, INSTANCE_ID);

    assertThat(gson.fromJson(gson.toJson(original), GroupIndexEvent.class)).isEqualTo(original);
  }

  @Test
  public void groupEventIndexRoundTripWithoutSha1() {
    String aGroupUUID = UUID.randomUUID().toString();
    GroupIndexEvent original = new GroupIndexEvent(aGroupUUID, null, INSTANCE_ID);

    assertThat(gson.fromJson(gson.toJson(original), GroupIndexEvent.class)).isEqualTo(original);
  }
}
