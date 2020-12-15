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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class CacheEntryTest {

  @Test
  public void cacheEntry() throws Exception {
    CacheEntry entry = CacheEntry.from("my_plugin.my_cache", "someOtherKey");
    assertThat(entry.getPluginName()).isEqualTo("my_plugin");
    assertThat(entry.getCacheName()).isEqualTo("my_cache");
    assertThat(entry.getKey()).isEqualTo("someOtherKey");
  }
}
