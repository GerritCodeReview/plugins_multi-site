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

import java.lang.reflect.Array;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;

public class InMemoryDfsRefDatabaseTest {

    private final ObjectId OBJECT_ID_1 =
            ObjectId.fromString("1049eb6eee7e1318f4e78e799bf33f1e54af9cbf");
    private final ObjectId OBJECT_ID_2 =
            ObjectId.fromString("2049eb6eee7e1318f4e78e799bf33f1e54af9cbf");
    private final ObjectId OBJECT_ID_3 =
            ObjectId.fromString("3049eb6eee7e1318f4e78e799bf33f1e54af9cbf");

    @Test
    public void shouldDeleteAnEntryAfterCreatingIt() {
        try (InMemoryDfsRefDatabase db = aNotSelfCleaningRefDb()) {
            db.createRef("ref", OBJECT_ID_1);
            assertThat(db.deleteRef("ref", OBJECT_ID_1)).isTrue();
        }
    }

    @Test
    public void shouldNotDeleteAnEntryIfStoredObjectIdIsNotTheGivenOne() {
        try (InMemoryDfsRefDatabase db = aNotSelfCleaningRefDb()) {
            db.createRef("ref", OBJECT_ID_1);
            assertThat(db.deleteRef("ref", OBJECT_ID_2)).isFalse();
        }
    }

    @Test
    public void shouldNotDeleteAnEntryTwice() {
        try (InMemoryDfsRefDatabase db = aNotSelfCleaningRefDb()) {
            db.createRef("ref", OBJECT_ID_1);
            db.deleteRef("ref", OBJECT_ID_1);
            assertThat(db.deleteRef("ref", OBJECT_ID_1)).isFalse();
        }
    }

    @Test
    public void shouldUpdateAnEntryAfterCreatingIt() {
        try (InMemoryDfsRefDatabase db = aNotSelfCleaningRefDb()) {
            db.createRef("ref", OBJECT_ID_1);
            assertThat(db.updateRefId("ref", OBJECT_ID_2, OBJECT_ID_1)).isTrue();
        }
    }

    @Test
    public void shouldUpdateAnEntryAfterUpdatingIt() {
        try (InMemoryDfsRefDatabase db = aNotSelfCleaningRefDb()) {
            db.createRef("ref", OBJECT_ID_1);
            assertThat(db.updateRefId("ref", OBJECT_ID_2, OBJECT_ID_1)).isTrue();
            assertThat(db.updateRefId("ref", OBJECT_ID_3, OBJECT_ID_2)).isTrue();
        }
    }

    @Test
    public void shouldNotUpdateAnEntryIfExpectedIdIsWrong() {
        try (InMemoryDfsRefDatabase db = aNotSelfCleaningRefDb()) {
            db.createRef("ref", OBJECT_ID_1);
            assertThat(db.updateRefId("ref", OBJECT_ID_3, OBJECT_ID_2)).isFalse();
        }
    }

    @Test
    public void shouldNotUpdateADeletedEntry() {
        try (InMemoryDfsRefDatabase db = aNotSelfCleaningRefDb()) {
            db.createRef("ref", OBJECT_ID_1);
            db.deleteRef("ref", OBJECT_ID_1);
            assertThat(db.updateRefId("ref", OBJECT_ID_2, OBJECT_ID_1)).isFalse();
        }
    }

    @Test
    public void shouldScheduleTheCleaner() throws InterruptedException {

        AtomicBoolean called = new AtomicBoolean(false);
        CountDownLatch barrier = new CountDownLatch(1);
        InMemoryDfsRefDatabaseCleaner cleaner =
                new InMemoryDfsRefDatabaseCleaner() {
                    @Override
                    public boolean shouldRemove(InMemoryDfsRefDatabase.TimedObjectId timedObjectId) {
                        called.set(true);
                        barrier.countDown();
                        return false;
                    }
                };

        try (InMemoryDfsRefDatabase db =
                     new InMemoryDfsRefDatabase(Executors.newSingleThreadScheduledExecutor(), cleaner, 1)) {
            db.createRef("ref", OBJECT_ID_1);

            barrier.await(3, SECONDS);

            assertThat(called.get()).isTrue();
        }
    }


    @SuppressWarnings("unchecked")
    @Test
    public void shouldBeThreadSafe() throws ExecutionException, InterruptedException {
        InMemoryDfsRefDatabase db = aNotSelfCleaningRefDb();

        final int writerCount = 50;
        final ExecutorService executorService = Executors.newFixedThreadPool(writerCount);

        final String refName = "refs/for/master";

        CountDownLatch allWritersTogether = new CountDownLatch(writerCount);
        Future<Boolean>[] changeAttemptResults = (Future[]) Array.newInstance(Future.class, writerCount);

        db.createRef(refName, OBJECT_ID_1);

        for (int i = 0; i < writerCount; i++) {
            final ObjectId newId = ObjectId.fromString(String.format("1049eb6eee7e1318f4e78e799bf33f1e54af9c%02d", i));
            changeAttemptResults[i] = executorService.submit(() -> {
                        allWritersTogether.countDown();
                        return db.updateRefId(refName, newId, OBJECT_ID_1);
                    }
            );
        }

        int successCount = 0;
        for(Future<Boolean> result : changeAttemptResults) {
            if(result.get()) {
                successCount++;
            }
        }

        assertThat(successCount).isEqualTo(1);
    }

    private InMemoryDfsRefDatabase aNotSelfCleaningRefDb() {
        return new InMemoryDfsRefDatabase();
    }
}
