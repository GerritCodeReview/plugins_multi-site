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

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Named;
import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.InstanceId;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import org.apache.commons.lang3.Validate;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.Locker;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ZkSharedRefDatabase implements SharedRefDatabase {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private final CuratorFramework client;
    private final Duration lockTimeout;
    private final UUID instanceId;

    @Inject
    public ZkSharedRefDatabase(CuratorFramework client,
                               @Named("ZkLockTimeout") Duration lockTimeout,
                               @InstanceId UUID instanceId) {
        this.client = client;
        this.lockTimeout = lockTimeout;
        this.instanceId = instanceId;
    }

    @Override
    public Ref newRef(String refName, ObjectId objectId) {
        return new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, refName, objectId);
    }

    @Override
    public boolean compareAndPut(String projectName, Ref oldRef, Ref newRef) throws IOException {
        boolean isCreate = oldRef == NULL_REF;

        try {
            if (isCreate) {
                return applyCreate(projectName, newRef);
            }

            return applyUpdate(projectName, oldRef, newRef);
        } catch (Exception e) {
            logger.atWarning().withCause(e).log("Error trying to perform CAS at path %s", ZkMarshaller.pathFor(projectName, newRef));
            throw new IOException(String.format("Error trying to perform CAS at path %s", ZkMarshaller.pathFor(projectName, newRef)), e);
        }
    }

    private boolean applyCreate(String projectName, Ref newRef) throws Exception {
        final ZkMarshaller marshaller = new ZkMarshaller(client);

        final InterProcessMutex refPathMutex = new InterProcessMutex(client, ZkMarshaller.pathFor(projectName, newRef));

        try(Locker locker = new Locker(refPathMutex, lockTimeout.toMillis(), MILLISECONDS)) {
            final Optional<ZkRefInfo> zkRefInfoMaybe = marshaller.readZkRefInfo(projectName, newRef.getName());

            if(zkRefInfoMaybe.isPresent()) {
                logger.atWarning().log("Asked to create ref %s but it is already in ZK at path %s",
                        newRef.getName(), ZkMarshaller.pathFor(projectName, newRef));
                return false;
            }

            marshaller.createRef(client.transaction(), client.transactionOp(), ZkRefInfo.newInstance(projectName, newRef, instanceId));

            return true;
        }
    }

    private boolean applyUpdate(String projectName, Ref oldRef, Ref newRef) throws Exception {
        Validate.isTrue(oldRef.getName().equalsIgnoreCase(newRef.getName()),
                "Name for new and old ref needs to match but was old '%s', new '%s'",
                oldRef.getName(),
                newRef.getName());

        final ZkMarshaller marshaller = new ZkMarshaller(client);

        final InterProcessMutex refPathMutex = new InterProcessMutex(client, ZkMarshaller.pathFor(projectName, newRef));

        try(Locker locker = new Locker(refPathMutex, lockTimeout.toMillis(), MILLISECONDS)) {
            final Optional<ZkRefInfo> zkRefInfoMaybe = marshaller.readZkRefInfo(projectName, newRef.getName());

            if(!zkRefInfoMaybe.isPresent()) {
                logger.atWarning().log("Asked to update ref %s but it is not in ZK at path %s",
                        newRef.getName(), ZkMarshaller.pathFor(projectName, newRef));
                return false;
            }

            if(!zkRefInfoMaybe.get().objectId().equals(oldRef.getObjectId())) {
                return false;
            }

            marshaller.updateRef(client.transaction(), client.transactionOp(), ZkRefInfo.newInstance(projectName, newRef, instanceId));

            return true;
        }
    }

    @Override
    public boolean compareAndRemove(String project, Ref oldRef) throws IOException {
        return compareAndPut(project, oldRef, tombstoneFor(oldRef));
    }

    private Ref tombstoneFor(final Ref oldRef) {
        return new Ref() {

            @Override
            public String getName() {
                return oldRef.getName();
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
        };
    }
}
