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

import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

@Singleton
public class InMemoryDfsRefDatabase implements SharedRefDatabase, AutoCloseable {

  @Override
  public void close() throws Exception {}

  @Override
  public Ref newRef(String refName, ObjectId objectId) {
    return null;
  }

  @Override
  public boolean compareAndPut(String project, Ref oldRef, Ref newRef) throws IOException {
    return false;
  }

  @Override
  public boolean compareAndRemove(String project, Ref oldRef) throws IOException {
    return false;
  }
}
