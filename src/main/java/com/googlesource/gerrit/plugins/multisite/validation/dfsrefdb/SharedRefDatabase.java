// Copyright (C) 2019 The Android Open Source Project
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

import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;

public interface SharedRefDatabase {
  Ref NULL_REF =
      new Ref() {

        @Override
        public String getName() {
          return null;
        }

        @Override
        public boolean isSymbolic() {
          return false;
        }

        @Override
        public Ref getLeaf() {
          return null;
        }

        @Override
        public Ref getTarget() {
          return null;
        }

        @Override
        public ObjectId getObjectId() {
          return null;
        }

        @Override
        public ObjectId getPeeledObjectId() {
          return null;
        }

        @Override
        public boolean isPeeled() {
          return false;
        }

        @Override
        public Storage getStorage() {
          return Storage.NEW;
        }
      };

  /**
   * Create a new in-memory Ref name associated with an objectId.
   *
   * @param refName ref name
   * @param objectId object id
   */
  default Ref newRef(String refName, ObjectId objectId) {
    return new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, refName, objectId);
  }

  /**
   * Utility method for new refs.
   *
   * @param project project name of the ref
   * @param newRef new reference to store.
   * @return true if the operation was successful; false otherwise.
   * @throws IOException
   */
  default boolean compareAndCreate(String project, Ref newRef) throws IOException {
    return compareAndPut(project, NULL_REF, newRef);
  }

  /**
   * Compare a reference, and put if it matches.
   *
   * <p>Two reference match if and only if they satisfy the following:
   *
   * <ul>
   *   <li>If one reference is a symbolic ref, the other one should be a symbolic ref.
   *   <li>If both are symbolic refs, the target names should be same.
   *   <li>If both are object ID refs, the object IDs should be same.
   * </ul>
   *
   * @param project project name of the ref
   * @param oldRef old value to compare to. If the reference is expected to not exist the old value
   *     has a storage of {@link org.eclipse.jgit.lib.Ref.Storage#NEW} and an ObjectId value of
   *     {@code null}.
   * @param newRef new reference to store.
   * @return true if the put was successful; false otherwise.
   * @throws java.io.IOException the reference cannot be put due to a system error.
   */
  boolean compareAndPut(String project, Ref oldRef, Ref newRef) throws IOException;

  /**
   * Compare a reference, and delete if it matches.
   *
   * @param project project name of the ref
   * @param oldRef the old reference information that was previously read.
   * @return true if the remove was successful; false otherwise.
   * @throws java.io.IOException the reference could not be removed due to a system error.
   */
  boolean compareAndRemove(String project, Ref oldRef) throws IOException;

  /**
   * Some references should not be stored in the SharedRefDatabase.
   *
   * @param ref
   * @return true if it's to be ignore; false otherwise
   */
  default boolean ignoreRefInSharedDb(Ref ref) {
    String refName = ref.getName();
    return refName.startsWith("refs/draft-comments")
        || (refName.startsWith("refs/changes") && !refName.endsWith("/meta"));
  }
}
