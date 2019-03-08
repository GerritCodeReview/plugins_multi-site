package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.memory;

import java.time.Duration;
import org.eclipse.jgit.lib.ObjectId;

public interface InMemoryDfsRefDatabaseCleaner {

  static InMemoryDfsRefDatabaseCleaner cleanIfNotTouchedFor(final Duration minAge) {
    return timedObjectId -> timedObjectId.age().compareTo(minAge) > 0;
  }

  static InMemoryDfsRefDatabaseCleaner cleanIfDeleted() {
    return timedObjectId -> timedObjectId.objectId == ObjectId.zeroId();
  }

  static InMemoryDfsRefDatabaseCleaner noop() {
    return timedObjectId -> false;
  }

  default void cleanup(InMemoryDfsRefDatabase db) {
    db.processAllEntries(
        (refName, timedObjectId) -> shouldRemove(timedObjectId) ? null : timedObjectId);
  }

  boolean shouldRemove(InMemoryDfsRefDatabase.TimedObjectId timedObjectId);
}
