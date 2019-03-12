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
import com.googlesource.gerrit.plugins.multisite.InstanceId;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Named;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.Locker;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

public class ZkSharedRefDatabase implements SharedRefDatabase {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CuratorFramework client;
  private final Duration lockTimeout;
  private final UUID instanceId;

  @Inject
  public ZkSharedRefDatabase(
      CuratorFramework client,
      @Named("ZkLockTimeout") Duration lockTimeout,
      @InstanceId UUID instanceId) {
    this.client = client;
    this.lockTimeout = lockTimeout;
    this.instanceId = instanceId;
  }

  @Override
  public boolean compareAndRemove(String project, Ref oldRef) throws IOException {
    return compareAndPut(project, oldRef, TombstoneRef.forRef(oldRef));
  }

  @Override
  public boolean compareAndPut(String projectName, Ref oldRef, Ref newRef) throws IOException {
    boolean isCreate = oldRef == NULL_REF;

    final ZkRefInfoDAO marshaller = new ZkRefInfoDAO(client);

    final InterProcessMutex refPathMutex =
        new InterProcessMutex(client, "/locks" + ZkRefInfoDAO.pathFor(projectName, newRef));

    try (Locker locker = new Locker(refPathMutex, lockTimeout.toMillis(), MILLISECONDS)) {
      final Optional<ZkRefInfo> infoCurrentlyInZkMaybe =
          marshaller.read(projectName, newRef.getName());
      final ZkRefInfo newRefInfo = new ZkRefInfo(projectName, newRef);

      if (isCreate) {
        return doCreate(marshaller, infoCurrentlyInZkMaybe, newRefInfo);
      }
      return doUpdate(oldRef, marshaller, infoCurrentlyInZkMaybe, newRefInfo);

    } catch (Exception e) {
      logger.atWarning().withCause(e).log(
          "Error trying to perform CAS at path %s", ZkRefInfoDAO.pathFor(projectName, newRef));
      throw new IOException(
          String.format(
              "Error trying to perform CAS at path %s", ZkRefInfoDAO.pathFor(projectName, newRef)),
          e);
    }
  }

  private boolean doUpdate(
      Ref oldRef,
      ZkRefInfoDAO marshaller,
      Optional<ZkRefInfo> infoCurrentlyInZkMaybe,
      ZkRefInfo newRefInfo)
      throws Exception {
    if (!infoCurrentlyInZkMaybe.isPresent()) {
      logger.atWarning().log(
          "Asked to update ref %s but it is not in ZK at path %s",
          newRefInfo.refName(), ZkRefInfoDAO.pathFor(newRefInfo));
      return false;
    }

    if (!infoCurrentlyInZkMaybe.get().objectId().equals(oldRef.getObjectId())) {
      logger.atWarning().log(
          "Old Ref %s does not match the current Rf content in Zookeeper %s. Not applying the update.",
          oldRef.getObjectId(), infoCurrentlyInZkMaybe.get().objectId());
      return false;
    }

    marshaller.update(newRefInfo);

    return true;
  }

  private boolean doCreate(
      ZkRefInfoDAO marshaller, Optional<ZkRefInfo> infoCurrentlyInZkMaybe, ZkRefInfo newRefInfo)
      throws Exception {
    if (infoCurrentlyInZkMaybe.isPresent()) {
      logger.atWarning().log(
          "Asked to create ref %s but it is already in ZK at path %s",
          newRefInfo.refName(), ZkRefInfoDAO.pathFor(newRefInfo));
      return false;
    }

    marshaller.create(newRefInfo);

    return true;
  }

  /**
   * When deleting a Ref this temporary Ref Tombstone will be created and then cleaned-up at a later
   * stage by the garbage collection
   */
  static class TombstoneRef implements Ref {
    static TombstoneRef forRef(final Ref targetRef) {
      return new TombstoneRef(targetRef.getName());
    }

    private final String name;

    private TombstoneRef(String name) {
      this.name = name;
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
      return ObjectId.zeroId();
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
