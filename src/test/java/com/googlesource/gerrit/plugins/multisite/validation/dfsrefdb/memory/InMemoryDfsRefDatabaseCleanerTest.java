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

package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.memory;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class InMemoryDfsRefDatabaseCleanerTest {

  @Test
  public void shouldIterateThroughAllDbElements() throws InterruptedException {
    final int dbEntries = 5;
    final Set<ObjectId> expectedVisitedIds = someObjectIds(dbEntries);
    final Set<ObjectId> visitedIds = new HashSet<>();
    final CountDownLatch barrier = new CountDownLatch(dbEntries);

    try (InMemoryDfsRefDatabase db =
        createDb(
            (timedObjectId) -> {
              visitedIds.add(timedObjectId.objectId);
              barrier.countDown();
              return false;
            })) {
      expectedVisitedIds.forEach(id -> db.createRef("ref/" + id, id));

      barrier.await(3, SECONDS);

      assertThat(visitedIds).isEqualTo(expectedVisitedIds);
    }
  }

  @Test
  public void ageCleanerShouldDiscardOldEntries() throws InterruptedException {
    final int dbEntries = 5;
    final Set<ObjectId> allEntries = someObjectIds(dbEntries);
    final Set<ObjectId> expectedInDb = new HashSet<>();
    expectedInDb.addAll(allEntries);
    expectedInDb.removeIf(id -> id.name().charAt(id.name().length() - 1) < '3');

    final CountDownLatch barrier = new CountDownLatch(dbEntries);

    try (InMemoryDfsRefDatabase db =
        createDb(
            (timedObjectId) -> {
              barrier.countDown();
              return !expectedInDb.contains(timedObjectId.objectId);
            })) {
      allEntries.forEach(id -> db.createRef("ref/" + id, id));

      barrier.await(3, SECONDS);

      allEntries.forEach(
          id -> {
            if (expectedInDb.contains(id)) {
              assertThat(db.deleteRef("ref/" + id, id)).isTrue();
            } else {
              try {
                db.deleteRef("ref/" + id, id);
                fail("Entry was supposed to be removed");
              } catch (NoSuchElementException expected) {

              }
            }
          });
    }
  }

  private Set<ObjectId> someObjectIds(int count) {
    Set<ObjectId> expectedVisitedIds = new HashSet<>();
    for (int i = 0; i < count; i++) {
      expectedVisitedIds.add(ObjectId.fromString("1049eb6eee7e1318f4e78e799bf33f1e54af9cb" + i));
    }
    return expectedVisitedIds;
  }

  private InMemoryDfsRefDatabase createDb(InMemoryDfsRefDatabaseCleaner cleaner) {
    return new InMemoryDfsRefDatabase(Executors.newSingleThreadScheduledExecutor(), cleaner, 1);
  }
}
