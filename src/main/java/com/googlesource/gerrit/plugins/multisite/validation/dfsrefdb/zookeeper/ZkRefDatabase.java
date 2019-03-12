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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Atomics;
import com.google.gson.Gson;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.framework.api.GetDataBuilder;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.DfsRefDatabase;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.Transport.Operation;
import org.eclipse.jgit.util.RefList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ref database using Zookeeper for consistent replication.
 * <p>
 * Delegates to a local ref database for actual storage, assuming this object is
 * the only writer to that database. Writers in other clusters write serialized
 * {@link PendingUpdate}s as they modify their own copies of the ref database,
 * and this local object is responsible for applying these updates to the local
 * database before accepting any writes from clients.
 */
// TODO(dborowitz): Make it safe to have multiple ZkRefDatabases open against
// the same underlying storage; this requires watching the local version,
// possibly locking around refs, etc.
public class ZkRefDatabase extends DfsRefDatabase {
    private static final Logger log =
            LoggerFactory.getLogger(ZkRefDatabase.class);

    private static final Gson GSON = new Gson();

    private static final Random RANDOM = new Random();

    private static byte[] intToBytes(int value) {
        byte[] buf = new byte[4];
        ByteBuffer.wrap(buf).putInt(value);
        return buf;
    }

    private static int bytesToInt(byte[] buf) {
        return ByteBuffer.wrap(buf).getInt();
    }

    private static String idToString(Ref ref) {
        return ObjectId.toString(ref.getObjectId());
    }

    private static class Value {
        private static final Value UNKNOWN = new Value(-1, -1);
        private final int value;
        private final int version;

        private Value(int value, int version) {
            this.value = value;
            this.version = version;
        }

        private Value next() {
            return new Value(value + 1, version + 1);
        }

        @Override
        public String toString() {
            return "Value[" + value + ", v=" + version + "]";
        }
    }

    private static class CountDownCallback implements BackgroundCallback {
        private final CountDownLatch done;
        private final AtomicReference<Throwable> err;

        private CountDownCallback(int count) {
            done = new CountDownLatch(count);
            err = Atomics.newReference();
        }

        @Override
        public void processResult(CuratorFramework client, CuratorEvent event) {
            Code code = Code.get(event.getResultCode());
            if (code != Code.OK && code != Code.NODEEXISTS) {
                err.compareAndSet(null, KeeperException.create(code, event.getPath()));
            }
            try {
                process(event);
            } catch (Throwable t) {
                err.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        }

        protected void process(CuratorEvent event) {
        }

        private void await() throws Exception {
            done.await();
            Throwable t = err.get();
            if (t != null) {
                Throwables.propagateIfPossible(t, Exception.class);
                throw new Exception(t);
            }
        }
    }

    private class ValueWatcher implements Watcher {
        private final String path;
        private final AtomicReference<Value> v;

        private ValueWatcher(String path) {
            this.path = path;
            this.v = Atomics.newReference(Value.UNKNOWN);
        }

        @Override
        public void process(WatchedEvent event) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (closed) {
                        return;
                    }
                    process();
                    readValue(path, v, ValueWatcher.this);
                }
            });
        }

        protected void process() {
        }

        private Value read() {
            return readValue(path, v, null);
        }
    }

    private final CuratorFramework client;
    private final String name;
    private final RefDatabase localDb;
    private final String localReplica;
    private final Executor executor;
    private final Map<String, RemoteConfig> replicaConfigs;

    private volatile boolean closed;
    private final AtomicReference<Value> localVersion =
            Atomics.newReference(Value.UNKNOWN);
    private final AtomicReference<Value> globalVersion =
            Atomics.newReference(Value.UNKNOWN);

    // TODO(dborowitz): Handle overflow in versions.
    private final ValueWatcher globalVersionWatcher;

    ZkRefDatabase(
            final CuratorFramework client,
            String name,
            DfsRepository repository,
            RefDatabase localDb,
            String localReplica,
            Executor executor,
            Collection<RemoteConfig> remotes) {
        super(repository);
        this.client = client;
        this.name = "/" + name;
        this.localDb = localDb;
        this.localReplica = localReplica;
        this.executor = executor;

        this.replicaConfigs = Maps.newHashMapWithExpectedSize(remotes.size());
        for (RemoteConfig remote : remotes) {
            replicaConfigs.put(remote.getName(), remote);
        }
        checkArgument(replicaConfigs.containsKey(localReplica),
                "No config for local replica");

        globalVersionWatcher = new ValueWatcher(globalVersionPath()) {
            @Override
            protected void process() {
                processUpdates();
            }
        };
    }

//    @Override
//    public boolean exists() throws IOException {
//        try {
//            CountDownCallback cb =
//                    new CountDownCallback(2 * replicaConfigs.size() + 2);
//            client.checkExists().inBackground(cb).forPath("");
//            client.checkExists().inBackground(cb).forPath(globalVersionPath());
//            for (String replica : replicaConfigs.keySet()) {
//                client.checkExists().inBackground(cb).forPath(replicaPath(replica));
//                client.checkExists().inBackground(cb).forPath(versionPath(replica));
//            }
//            cb.await();
//            return true;
//        } catch (KeeperException.NoNodeException e) {
//            return false;
//        } catch (IOException e) {
//            throw e;
//        } catch (Exception e) {
//            throw new IOException(e);
//        }
//    }

    @Override
    public void create() {
        try {
            // /project
            try {
                client.create().creatingParentsIfNeeded().forPath(name);
            } catch (KeeperException.NodeExistsException e) {
                // Ignore.
            }

            // /project/version, /project/<replica>
            byte[] zero = intToBytes(0);
            CountDownCallback cb = new CountDownCallback(replicaConfigs.size() + 1);
            client.create().inBackground(cb).forPath(globalVersionPath(), zero);
            for (String replica : replicaConfigs.keySet()) {
                client.create().inBackground(cb).forPath(replicaPath(replica));
            }
            cb.await();

            // /project/<replica>/version
            cb = new CountDownCallback(replicaConfigs.size());
            for (String replica : replicaConfigs.keySet()) {
                client.create().inBackground(cb).forPath(versionPath(replica), zero);
            }
            cb.await();
        } catch (Exception e) {
            throw new RuntimeException( "Unable to create db", e);
        }
    }

    @Override
    public void close() {
        closed = true;
        localDb.close();
    }

    @Override
    protected RefCache scanAllRefs() throws IOException {
        Map<String, Ref> refs = localDb.getRefs(RefDatabase.ALL);
        RefList.Builder<Ref> ids = new RefList.Builder<Ref>(refs.size());
        RefList.Builder<Ref> sym = new RefList.Builder<Ref>();
        for (Ref ref : refs.values()) {
            if (ref.isSymbolic()) {
                sym.add(ref);
            }
            ids.add(ref);
        }
        ids.sort();
        sym.sort();
        return new RefCache(ids.toRefList(), sym.toRefList());
    }

    @Override
    protected boolean compareAndPut(Ref oldRef, Ref newRef) throws IOException {
        PendingUpdate update = new PendingUpdate();
        update.refName = newRef.getName();
        update.oldId = idToString(oldRef);
        update.newId = idToString(newRef);
        // TODO(dborowitz): Object quorum.
        update.sourceReplicas = ImmutableList.of(localReplica);
        return writeUpdate(update);
    }

    @Override
    protected boolean compareAndRemove(Ref oldRef) throws IOException {
        PendingUpdate update = new PendingUpdate();
        update.refName = oldRef.getName();
        update.oldId = idToString(oldRef);
        update.newId = ObjectId.toString(ObjectId.zeroId());
        // TODO(dborowitz): Object quorum.
        update.sourceReplicas = ImmutableList.of(localReplica);
        return writeUpdate(update);
    }

    @Override
    protected void cachePeeledState(Ref oldLeaf, Ref newLeaf) {
        // Peeled state is not cached in Zookeeper.
    }

    public void start() throws IOException {
        try {
            new Exception("Starting!").printStackTrace();
            client.getData().usingWatcher(globalVersionWatcher).inBackground()
                    .forPath(globalVersionPath());
            readValue(localVersionPath(), localVersion, null);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private String globalVersionPath() {
        return name + "/version";
    }

    private String replicaPath(String replica) {
        return name + "/" + replica;
    }

    private String versionPath(String replica) {
        return replicaPath(replica) + "/version";
    }

    private String updatePath(String replica, int repoVersion) {
        return replicaPath(replica) + "/update_" + repoVersion;
    }

    private String localVersionPath() {
        return versionPath(localReplica);
    }

    private int updateVersion(String path) {
        String prefix = replicaPath(localReplica) + "/update_";
        checkArgument(path.startsWith(prefix),
                "Invalid update path: %s (should start with %s)", path, prefix);
        return Integer.parseInt(path.substring(prefix.length()));
    }

    private Value readValue(String path, AtomicReference<Value> v, Watcher watcher) {
        try {
            Stat stat = new Stat();
            GetDataBuilder get = client.getData();
            get.storingStatIn(stat);
            if (watcher != null) {
                get.usingWatcher(watcher);
            }
            Value nv = new Value(bytesToInt(get.forPath(path)), stat.getVersion());
            v.set(nv);
            return nv;
        } catch (Exception e) {
            log.error("Error reading value at " + path, e);
            return null;
        }
    }

    private synchronized void processUpdates() {
        try {
            Value local = localVersion.get();

            List<String> toDelete = Lists.newArrayList();
            List<String> toApply = Lists.newArrayList();
            String rp = replicaPath(localReplica);
            for (String path : client.getChildren().forPath(rp)) {
                path = rp + "/" + path;
                if (!path.endsWith("/version")) {
                    if (updateVersion(path) > local.value) {
                        toApply.add(path);
                    } else {
                        toDelete.add(path);
                    }
                }
            }

            CountDownCallback deleteCb;
            if (!toDelete.isEmpty()) {
                deleteCb = new CountDownCallback(toDelete.size());
                for (String path : toDelete) {
                    client.delete().inBackground(deleteCb).forPath(path);
                }
            } else {
                deleteCb = null;
            }

            final List<PendingUpdate> updates = Lists.newArrayListWithCapacity(
                    toApply.size());
            CountDownCallback cb = new CountDownCallback(toApply.size()) {
                @Override
                protected void process(CuratorEvent event) {
                    PendingUpdate u = GSON.fromJson(
                            new String(event.getData(), Charsets.UTF_8), PendingUpdate.class);
                    u.repoVersion = updateVersion(event.getPath());
                    u.path = event.getPath();
                    updates.add(u);
                }
            };
            for (String path : toApply) {
                client.getData().inBackground(cb).forPath(path);
            }
            cb.await();
            Collections.sort(updates);

            for (PendingUpdate update : updates) {
                checkState(update.repoVersion == local.value + 1,
                        "Next update version %s does not match "
                                + "expected next local version %s",
                        update.repoVersion, local.value + 1);
                // TODO(dborowitz): Batch multiple outstanding fetches (may have to use
                // different replicas, etc.).
                fetchObjects(update);
                applyUpdateLocally(update);
                client.inTransaction()
                        .delete().forPath(update.path)
                        .and().setData().withVersion(local.version).forPath(
                        localVersionPath(), intToBytes(update.repoVersion))
                        .and().commit();
                localVersion.set(local.next());
            }

            if (deleteCb != null) {
                deleteCb.await();
            }
        } catch (Exception e) {
            log.error("Error processing update", e);
        }
    }

    private RemoteConfig pickReplica(List<String> replicaNames) {
        // TODO(dborowitz): Better load balancing, e.g. choosing a random URI
        // instead of the first one.
        return replicaConfigs.get(
                replicaNames.get(RANDOM.nextInt(replicaNames.size())));
    }

    private void fetchObjects(PendingUpdate update) throws NotSupportedException,
            TransportException {
        if (update.sourceReplicas.contains(localReplica)) {
            return;
        }
        RemoteConfig remote = pickReplica(update.sourceReplicas);
        Ref want = new ObjectIdRef.Unpeeled(Storage.NETWORK, update.newId,
                ObjectId.fromString(update.newId));
        Transport t = Transport.open(getRepository(), remote, Operation.FETCH);
        t.openFetch().fetch(NullProgressMonitor.INSTANCE,
                Collections.singleton(want), Collections.<ObjectId> emptySet());
    }

    private synchronized boolean writeUpdate(PendingUpdate update)
            throws IOException {
        Value global = globalVersionWatcher.read();
        Value local = localVersion.get();
        if (local.value != global.value) {
            // Local view is not current, and this write won't succeed until we
            // process the necessary updates.
            return false;
        }

        int next = local.value + 1;
        byte[] nb = intToBytes(next);
        byte[] data = GSON.toJson(update).getBytes(Charsets.UTF_8);

        // TODO(dborowitz): Support retrying against a higher version number if
        // there are non-conflicting updates. Requires checking the refnames of any
        // pending updates, possibly storing refnames in update node names.
        try {
            CuratorTransactionFinal t = client.inTransaction()
                    .setData().withVersion(local.version).forPath(localVersionPath(), nb)
                    .and().setData().withVersion(global.version).forPath(
                            globalVersionPath(), nb)
                    .and();
            for (String replica : replicaConfigs.keySet()) {
                t.create().forPath(updatePath(replica, next), data);
            }
            t.commit();

            applyUpdateLocally(update);

            localVersion.set(local.next());
            // Another global update may have happened between the end of the
            // transaction and now.
            globalVersion.compareAndSet(global, global.next());
        } catch (KeeperException.BadVersionException | KeeperException.NodeExistsException e) {
            // Lost race on local or global version or on creating update
            return false;
        } // Lost race .
        catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
        return true;
    }

    private void applyUpdateLocally(PendingUpdate update) throws IOException {
        RefUpdate ru = localDb.newUpdate(update.refName, false);
        ru.setExpectedOldObjectId(ObjectId.fromString(update.oldId));
        ru.setNewObjectId(ObjectId.fromString(update.newId));
        ru.update();
    }
}