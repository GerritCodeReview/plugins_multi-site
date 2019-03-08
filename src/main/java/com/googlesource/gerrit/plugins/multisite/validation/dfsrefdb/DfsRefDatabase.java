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

package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb;

import java.util.NoSuchElementException;
import org.eclipse.jgit.lib.ObjectId;

public interface DfsRefDatabase {
  /**
   * Performs a compare and swap atomic operation on the given refName, setting the value for its
   * current ID to the given newId if the value in the DB is equal to the given expected one and the
   * entry hasn't been deleted.
   *
   * @param refName the name of the ref object to update
   * @param newId the value of the current ID to be set in the DB
   * @param expectedOldId the expected previous value
   * @return true if the operation has been performed successfully and false if it failed because
   *     the value is different
   * @throws NoSuchElementException if no entry is found for refName in the DB. Trying to update a
   *     deleted entry won't throw this exception but just return false.
   */
  boolean updateRefId(String refName, ObjectId newId, ObjectId expectedOldId)
      throws NoSuchElementException;

  /**
   * Creates a new entry in the DB for the given refName with the specified ID
   *
   * @param refName the name of the ref object to create
   * @param id the id of the new ref
   * @throws IllegalArgumentException if the entry is already present in the DB (even if in a DELETE
   *     state)
   */
  void createRef(String refName, ObjectId id) throws IllegalArgumentException;

  /**
   * Sets the status for the given ref into DELETED state. Any further update won't be possible.
   * Creation of an entry with the same refName will throw an exception. The operation is performed
   * only if the id of the reference is equal to the given one.
   *
   * @param refName the name of the ref to delete
   * @param expectedId the ID of the reference expected by the caller
   * @throws NoSuchElementException if no entry is found in the DB for the given reference name
   */
  boolean deleteRef(String refName, ObjectId expectedId) throws NoSuchElementException;
}
