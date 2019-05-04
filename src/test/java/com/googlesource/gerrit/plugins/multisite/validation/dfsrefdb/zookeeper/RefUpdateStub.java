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

import java.io.IOException;
import org.apache.commons.lang.NotImplementedException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Ignore;

@Ignore
public class RefUpdateStub extends RefUpdate {

  public static RefUpdate forSuccessfulCreate(Ref newRef) {
    return new RefUpdateStub(Result.NEW, null, newRef, newRef.getObjectId());
  }

  public static RefUpdate forSuccessfulUpdate(Ref oldRef, ObjectId newObjectId) {
    return new RefUpdateStub(Result.FAST_FORWARD, null, oldRef, newObjectId);
  }

  public static RefUpdate forSuccessfulDelete(Ref oldRef) {
    return new RefUpdateStub(null, Result.FORCED, oldRef, ObjectId.zeroId());
  }

  private final Result updateResult;
  private final Result deleteResult;

  public RefUpdateStub(Result updateResult, Result deleteResult, Ref oldRef, ObjectId newObjectId) {
    super(oldRef);
    this.setNewObjectId(newObjectId);
    this.updateResult = updateResult;
    this.deleteResult = deleteResult;
  }

  @Override
  protected RefDatabase getRefDatabase() {
    throw new NotImplementedException("Method not implemented yet, not assumed you needed it!!");
  }

  @Override
  protected Repository getRepository() {
    throw new NotImplementedException("Method not implemented yet, not assumed you needed it!!");
  }

  @Override
  protected boolean tryLock(boolean deref) throws IOException {
    throw new NotImplementedException("Method not implemented yet, not assumed you needed it!!");
  }

  @Override
  protected void unlock() {
    throw new NotImplementedException("Method not implemented yet, not assumed you needed it!!");
  }

  @Override
  protected Result doUpdate(Result desiredResult) throws IOException {
    throw new NotImplementedException("Method not implemented, shouldn't be called!!");
  }

  @Override
  protected Result doDelete(Result desiredResult) throws IOException {
    throw new NotImplementedException("Method not implemented, shouldn't be called!!");
  }

  @Override
  protected Result doLink(String target) throws IOException {
    throw new NotImplementedException("Method not implemented yet, not assumed you needed it!!");
  }

  @Override
  public Result update() throws IOException {
    if (updateResult != null) return updateResult;

    throw new NotImplementedException("Not assumed you needed to stub this call!!");
  }

  @Override
  public Result update(RevWalk walk) throws IOException {
    if (updateResult != null) return updateResult;

    throw new NotImplementedException("Not assumed you needed to stub this call!!");
  }

  @Override
  public Result delete() throws IOException {
    if (deleteResult != null) return deleteResult;

    throw new NotImplementedException("Not assumed you needed to stub this call!!");
  }

  @Override
  public Result delete(RevWalk walk) throws IOException {
    if (deleteResult != null) return deleteResult;

    throw new NotImplementedException("Not assumed you needed to stub this call!!");
  }
}
