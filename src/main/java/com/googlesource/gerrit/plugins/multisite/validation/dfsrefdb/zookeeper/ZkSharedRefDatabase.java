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

import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import javax.inject.Named;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicValue;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

public class ZkSharedRefDatabase implements SharedRefDatabase {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CuratorFramework client;
  private final RetryPolicy retryPolicy;

  private final OperationMode operationMode;

  public enum OperationMode {
    NORMAL,
    MIGRATION;
  }

  @Inject
  public ZkSharedRefDatabase(
      CuratorFramework client,
      @Named("ZkLockRetryPolicy") RetryPolicy retryPolicy,
      OperationMode operationMode) {
    this.client = client;
    this.retryPolicy = retryPolicy;
    this.operationMode = operationMode;
  }

  @Override
  public boolean compareAndRemove(String project, Ref oldRef) throws IOException {
    return compareAndPut(project, oldRef, NULL_REF);
  }

  @Override
  public boolean compareAndPut(String projectName, Ref oldRef, Ref newRef) throws IOException {
    DistributedAtomicValue distributedRefValue;
    try {
      if (oldRef == NULL_REF) {
        distributedRefValue = new DistributedAtomicValue(client, pathFor(projectName, newRef), retryPolicy);
        return distributedRefValue.initialize(writeObjectId(newRef.getObjectId()));
      } else {
        distributedRefValue = new DistributedAtomicValue(client, pathFor(projectName, oldRef), retryPolicy);
        final ObjectId newValue =
            newRef.getObjectId() == null ? ObjectId.zeroId() : newRef.getObjectId();
        final AtomicValue<byte[]> newDistributedValue =
            distributedRefValue.compareAndSet(
                writeObjectId(oldRef.getObjectId()), writeObjectId(newValue));

        if (!newDistributedValue.succeeded()
            && operationMode == OperationMode.MIGRATION
            && refNotInZk(projectName, oldRef)) {
          logger.atInfo().log(
              "Missing entry in ZK for ref at path '%'. Assuming this is because we are in migration mode and creating it",
              pathFor(projectName, oldRef));

          return distributedRefValue.initialize(writeObjectId(newRef.getObjectId()));
        }

        return newDistributedValue.succeeded();
      }
    } catch (Exception e) {
      logger.atWarning().withCause(e).log(
          "Error trying to perform CAS at path %s", pathFor(projectName, newRef));
      throw new IOException(
          String.format("Error trying to perform CAS at path %s", pathFor(projectName, newRef)), e);
    }
  }

  private boolean refNotInZk(String projectName, Ref oldRef) throws Exception {
    return client.checkExists().forPath(pathFor(projectName, oldRef)) == null;
  }

  static String pathFor(String projectName, Ref ref) {
    return pathFor(projectName, ref.getName());
  }

  static String pathFor(String projectName, String refName) {
    return "/" + projectName + "/" + refName;
  }

  static ObjectId readObjectId(byte[] value) {
    return ObjectId.fromRaw(value);
  }

  static byte[] writeObjectId(ObjectId value) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final DataOutputStream stream = new DataOutputStream(out);
    value.copyRawTo(stream);
    return out.toByteArray();
  }
}
