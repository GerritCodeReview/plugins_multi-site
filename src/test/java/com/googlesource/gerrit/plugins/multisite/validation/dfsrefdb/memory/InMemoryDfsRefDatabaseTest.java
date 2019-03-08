package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.memory;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

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

  private InMemoryDfsRefDatabase aNotSelfCleaningRefDb() {
    return new InMemoryDfsRefDatabase(
        Executors.newSingleThreadScheduledExecutor(), InMemoryDfsRefDatabaseCleaner.noop(), 10);
  }
}
