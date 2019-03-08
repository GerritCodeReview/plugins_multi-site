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

public class NoOpDfsRefDatabase implements DfsRefDatabase {
  @Override
  public boolean updateRefId(String refName, ObjectId newId, ObjectId expectedOldId)
      throws NoSuchElementException {
    return true;
  }

  @Override
  public void createRef(String refName, ObjectId id) throws IllegalArgumentException {}

  @Override
  public boolean deleteRef(String refName, ObjectId expectedId) throws NoSuchElementException {
    return true;
  }
}
