// Copyright (C) 2012 The Android Open Source Project
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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.gerritforge.gerrit.globalrefdb.GlobalRefDatabase;
import com.gerritforge.gerrit.globalrefdb.GlobalRefDbLockException;
import com.gerritforge.gerrit.globalrefdb.GlobalRefDbSystemError;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.validation.ZkConnectionConfig;
import java.nio.charset.StandardCharsets;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicValue;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.Locker;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

public class ZkSharedRefDatabase implements GlobalRefDatabase {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CuratorFramework client;
  private final RetryPolicy retryPolicy;

  private final Long transactionLockTimeOut;

  @Inject
  public ZkSharedRefDatabase(CuratorFramework client, ZkConnectionConfig connConfig) {
    this.client = client;
    this.retryPolicy = connConfig.curatorRetryPolicy;
    this.transactionLockTimeOut = connConfig.transactionLockTimeout;
  }

  @Override
  public boolean isUpToDate(Project.NameKey project, Ref ref) throws GlobalRefDbLockException {
    if (!exists(project, ref.getName())) {
      return true;
    }

    try {
      final byte[] valueInZk = client.getData().forPath(pathFor(project, ref.getName()));

      // Assuming this is a delete node NULL_REF
      if (valueInZk == null) {
        logger.atInfo().log(
            "%s:%s not found in Zookeeper, assumed as delete node NULL_REF",
            project, ref.getName());
        return false;
      }

      ObjectId objectIdInSharedRefDb = readObjectId(valueInZk);
      Boolean isUpToDate = objectIdInSharedRefDb.equals(ref.getObjectId());

      if (!isUpToDate) {
        logger.atWarning().log(
            "%s:%s is out of sync: local=%s zk=%s",
            project, ref.getName(), ref.getObjectId(), objectIdInSharedRefDb);
      }

      return isUpToDate;
    } catch (Exception e) {
      throw new GlobalRefDbLockException(project.get(), ref.getName(), e);
    }
  }

  @Override
  public void remove(Project.NameKey project) throws GlobalRefDbSystemError {
    try {
      client.delete().deletingChildrenIfNeeded().forPath("/" + project);
    } catch (Exception e) {
      throw new GlobalRefDbSystemError(
          String.format("Not able to delete project '%s'", project), e);
    }
  }

  @Override
  public boolean exists(Project.NameKey project, String refName) throws GlobalRefDbSystemError {
    try {
      return client.checkExists().forPath(pathFor(project, refName)) != null;
    } catch (Exception e) {
      throw new ZookeeperRuntimeException("Failed to check if path exists in Zookeeper", e);
    }
  }

  @Override
  public Locker lockRef(Project.NameKey project, String refName) throws GlobalRefDbLockException {
    InterProcessMutex refPathMutex =
        new InterProcessMutex(client, "/locks" + pathFor(project, refName));
    try {
      return new Locker(refPathMutex, transactionLockTimeOut, MILLISECONDS);
    } catch (Exception e) {
      throw new GlobalRefDbLockException(project.get(), refName, e);
    }
  }

  @Override
  public boolean compareAndPut(Project.NameKey projectName, Ref oldRef, ObjectId newRefValue)
      throws GlobalRefDbSystemError {

    final DistributedAtomicValue distributedRefValue =
        new DistributedAtomicValue(client, pathFor(projectName, oldRef), retryPolicy);

    try {
      if ((oldRef.getObjectId() == null || oldRef.getObjectId().equals(ObjectId.zeroId()))
          && !distributedRefValue.get().succeeded()) {
        return distributedRefValue.initialize(writeObjectId(newRefValue));
      }
      final ObjectId newValue = newRefValue == null ? ObjectId.zeroId() : newRefValue;
      final AtomicValue<byte[]> newDistributedValue =
          distributedRefValue.compareAndSet(
              writeObjectId(oldRef.getObjectId()), writeObjectId(newValue));

      if (!newDistributedValue.succeeded() && refNotInZk(projectName, oldRef)) {
        return distributedRefValue.initialize(writeObjectId(newRefValue));
      }

      return newDistributedValue.succeeded();
    } catch (Exception e) {
      logger.atWarning().withCause(e).log(
          "Error trying to perform CAS at path %s", pathFor(projectName, oldRef));
      throw new GlobalRefDbSystemError(
          String.format("Error trying to perform CAS at path %s", pathFor(projectName, oldRef)), e);
    }
  }

  private boolean refNotInZk(Project.NameKey projectName, Ref oldRef) throws Exception {
    return client.checkExists().forPath(pathFor(projectName, oldRef)) == null;
  }

  static String pathFor(Project.NameKey projectName, Ref oldRef) {
    return pathFor(projectName, oldRef.getName());
  }

  static String pathFor(Project.NameKey projectName, String refName) {
    return "/" + projectName + "/" + refName;
  }

  static ObjectId readObjectId(byte[] value) {
    return ObjectId.fromString(value, 0);
  }

  static byte[] writeObjectId(ObjectId value) {
    return ObjectId.toString(value).getBytes(StandardCharsets.US_ASCII);
  }
}
