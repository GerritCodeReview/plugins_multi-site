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

package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper;

import org.apache.commons.lang.RandomStringUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.junit.Ignore;

@Ignore
public interface RefFixture {

  String ALLOWED_CHARS = "abcdefghilmnopqrstuvz";
  String ALLOWED_DIGITS = "1234567890";
  String ALLOWED_NAME_CHARS = ALLOWED_CHARS + ALLOWED_CHARS.toUpperCase() + ALLOWED_DIGITS;

  static String aProjectName() {
    return RandomStringUtils.random(20, ALLOWED_NAME_CHARS);
  }

  static ObjectId anObjectId() {
    return ObjectId.fromString(RandomStringUtils.randomNumeric(40));
  }

  static String aChangeRefName() {
    return "refs/for/" + RandomStringUtils.random(10, ALLOWED_NAME_CHARS);
  }

  static Ref aRefObject(String refName, ObjectId objectId) {
    return new TestRef(refName, objectId);
  }

  static Ref aRefObject(String refName) {
    return aRefObject(refName, anObjectId());
  }

  static Ref aRefObject() {
    return aRefObject(aChangeRefName(), anObjectId());
  }

  class TestRef implements Ref {
    private final String name;
    private ObjectId objectId;

    private TestRef(String name, ObjectId objectId) {
      this.name = name;
      this.objectId = objectId;
    }

    @Override
    public String getName() {
      return name;
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
      return objectId;
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
      return Storage.NETWORK;
    }
  }
}
