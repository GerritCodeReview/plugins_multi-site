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

import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.validation.ZkConnectionConfig;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedLockException;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicValue;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.Locker;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

public class ZkSharedRefDatabase implements SharedRefDatabase {
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
  public boolean isUpToDate(String project, Ref ref) throws SharedLockException {
    if (!exists(project, ref.getName())) {
      return true;
    }

    try {
      final byte[] valueInZk = client.getData().forPath(pathFor(project, ref.getName()));

      // Assuming this is a delete node NULL_REF
      if (valueInZk == null) {
        return false;
      }

      return readObjectId(valueInZk).equals(ref.getObjectId());
    } catch (Exception e) {
      throw new SharedLockException(project, ref.getName(), e);
    }
  }

  @Override
  public boolean compareAndRemove(String project, Ref oldRef) throws IOException {
    return compareAndPut(project, oldRef, ObjectId.zeroId());
  }

  @Override
  public boolean exists(String project, String refName) throws ZookeeperRuntimeException {
    try {
      return client.checkExists().forPath(pathFor(project, refName)) != null;
    } catch (Exception e) {
      throw new ZookeeperRuntimeException("Failed to check if path exists in Zookeeper", e);
    }
  }

  @Override
  public Locker lockRef(String project, String refName) throws SharedLockException {
    InterProcessMutex refPathMutex =
        new InterProcessMutex(client, "/locks" + pathFor(project, refName));
    try {
      return new Locker(refPathMutex, transactionLockTimeOut, MILLISECONDS);
    } catch (Exception e) {
      throw new SharedLockException(project, refName, e);
    }
  }

  @Override
  public boolean compareAndPut(String projectName, Ref oldRef, ObjectId newRefValue)
      throws IOException {

    final DistributedAtomicValue distributedRefValue =
        new DistributedAtomicValue(client, pathFor(projectName, oldRef), retryPolicy);

    try {
      if (oldRef == NULL_REF) {
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
      throw new IOException(
          String.format("Error trying to perform CAS at path %s", pathFor(projectName, oldRef)), e);
    }
  }

  private boolean refNotInZk(String projectName, Ref oldRef) throws Exception {
    return client.checkExists().forPath(pathFor(projectName, oldRef)) == null;
  }

  static String pathFor(String projectName, Ref oldRef) {
    return pathFor(projectName, oldRef.getName());
  }

  static String pathFor(String projectName, String refName) {
    return "/" + projectName + "/" + refName;
  }

  static ObjectId readObjectId(byte[] value) {
    return ObjectId.fromString(value, 0);
  }

  static byte[] writeObjectId(ObjectId value) {
    return ObjectId.toString(value).getBytes(StandardCharsets.US_ASCII);
  }
}
