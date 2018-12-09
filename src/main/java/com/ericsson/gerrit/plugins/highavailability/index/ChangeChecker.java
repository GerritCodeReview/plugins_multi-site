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

package com.ericsson.gerrit.plugins.highavailability.index;

import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.Optional;

/** Encapsulates the logic of verifying the up-to-date status of a change. */
public interface ChangeChecker {

  /**
   * Return the Change nodes read from ReviewDb or NoteDb.
   *
   * @return notes of the Change
   * @throws OrmException if ReviewDb or NoteDb cannot be opened
   */
  public Optional<ChangeNotes> getChangeNotes() throws OrmException;

  /**
   * Create a new index event POJO associated with the current Change.
   *
   * @return new IndexEvent
   * @throws IOException if the current Change cannot read
   * @throws OrmException if ReviewDb cannot be opened
   */
  public Optional<IndexEvent> newIndexEvent() throws IOException, OrmException;

  /**
   * Check if the local Change is aligned with the indexEvent received.
   *
   * @param indexEvent indexing event
   * @return true if the local Change is up-to-date, false otherwise.
   * @throws IOException if an I/O error occurred while reading the local Change
   * @throws OrmException if the local ReviewDb cannot be opened
   */
  public boolean isChangeUpToDate(Optional<IndexEvent> indexEvent) throws IOException, OrmException;

  /**
   * Return the last computed up-to-date Change time-stamp.
   *
   * <p>Compute the up-to-date Change time-stamp when it is invoked for the very first time.
   *
   * @return the Change timestamp epoch in seconds
   * @throws IOException if an I/O error occurred while reading the local Change
   * @throws OrmException if the local ReviewDb cannot be opened
   */
  public Optional<Long> getComputedChangeTs() throws IOException, OrmException;
}
