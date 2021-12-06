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

package com.googlesource.gerrit.plugins.multisite.consumer;

import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.serialize.JavaCacheSerializer;
import com.google.gerrit.server.cache.serialize.StringCacheSerializer;
import com.google.inject.Singleton;

@Singleton
public class ReplicationStatusCacheModule extends CacheModule {
  static final String REPLICATION_STATUS_CACHE = "replication_status";

  @Override
  protected void configure() {
    persist(REPLICATION_STATUS_CACHE, String.class, Long.class)
        .version(1)
        .keySerializer(StringCacheSerializer.INSTANCE)
        .valueSerializer(new JavaCacheSerializer<>());
  }
}
