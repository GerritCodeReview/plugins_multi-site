package com.googlesource.gerrit.plugins.multisite.index;

import com.googlesource.gerrit.plugins.multisite.forwarder.events.IndexEvent;
import java.util.Optional;

public interface UpToDateChecker<E extends IndexEvent> {
  /**
   * Check if the local Change is aligned with the indexEvent received.
   *
   * @param indexEvent indexing event
   * @return true if the local Change is up-to-date, false otherwise.
   */
  boolean isUpToDate(Optional<E> indexEvent);
}
