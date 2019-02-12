// Copyright (C) 2018 The Android Open Source Project
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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class IndexEvent {
  public long eventCreatedOn = System.currentTimeMillis() / 1000;
  public String targetSha;

  @Override
  public String toString() {
    return "IndexEvent@" + format(eventCreatedOn) + ((targetSha != null) ? "/" + targetSha : "");
  }

  public static String format(long eventTs) {
    return LocalDateTime.ofEpochSecond(eventTs, 0, ZoneOffset.UTC)
        .format(DateTimeFormatter.ISO_DATE_TIME);
  }
}
