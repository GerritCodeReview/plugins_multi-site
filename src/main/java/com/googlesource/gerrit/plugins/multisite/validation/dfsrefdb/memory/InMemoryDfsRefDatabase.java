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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DfsRefDatabase;
import org.eclipse.jgit.lib.ObjectId;

import static java.time.ZoneOffset.UTC;

public class InMemoryDfsRefDatabase implements DfsRefDatabase, AutoCloseable {

    static class TimedObjectId {
        public final ObjectId objectId;
        public final LocalDateTime lastUpdated;

        private TimedObjectId(ObjectId objectId) {
            this(objectId, LocalDateTime.now(UTC));
        }

        private TimedObjectId(ObjectId objectId, LocalDateTime lastUpdated) {
            this.objectId = objectId;
            this.lastUpdated = lastUpdated;
        }

        public TimedObjectId updated() {
            return new TimedObjectId(objectId);
        }

        public Duration age() {
            return Duration.between(lastUpdated, LocalDateTime.now(UTC));
        }
    }

    private final ConcurrentMap<String, TimedObjectId> refDb;
    private final ScheduledFuture<?> cleanerScheduledFuture;

    public InMemoryDfsRefDatabase(ScheduledExecutorService cleanerExecutor,
                                  InMemoryDfsRefDatabaseCleaner cleaner, long cleanupIntervalSeconds) {
        refDb = new ConcurrentHashMap<>();

        this.cleanerScheduledFuture = cleanerExecutor.scheduleAtFixedRate(
                () -> cleaner.cleanup(this),
                cleanupIntervalSeconds,
                cleanupIntervalSeconds,
                TimeUnit.SECONDS
        );
    }

    @Override
    public void close()  {
        cleanerScheduledFuture.cancel(true);
    }

    @Override
    public boolean updateRefId(String refName, ObjectId newId, ObjectId expectedOldId) throws NoSuchElementException {
        Objects.requireNonNull(refName);
        Objects.requireNonNull(newId);
        Objects.requireNonNull(expectedOldId);

        if (!refDb.containsKey(refName)) {
            throw new NoSuchElementException(String.format("No value in db for ref %s", refName));
        }

        final TimedObjectId toInsert = new TimedObjectId(newId);
        final TimedObjectId insertedValue = refDb.computeIfPresent(
                refName,
                (key, oldValue) -> oldValue.objectId.equals(expectedOldId) ? toInsert : oldValue
        );

        return insertedValue == toInsert;
    }

    @Override
    public void createRef(String refName, ObjectId id) throws IllegalArgumentException {
        Objects.requireNonNull(refName);
        Objects.requireNonNull(id);

        final TimedObjectId oldValue = refDb.get(refName);
        if (oldValue != null) {
            throw new IllegalArgumentException(String.format("Ref '%s' is already in DB with value %s",
                    refName, oldValue.objectId));
        }

        refDb.putIfAbsent(refName, new TimedObjectId(id));
    }

    @Override
    public boolean deleteRef(String refName, ObjectId expectedId) throws NoSuchElementException {
        return updateRefId(refName, ObjectId.zeroId(), expectedId);
    }

    void processAllEntries(BiFunction<String, TimedObjectId, TimedObjectId> processingFunction) {
        refDb.keySet().forEach(key -> refDb.computeIfPresent(key, processingFunction));
    }
}
