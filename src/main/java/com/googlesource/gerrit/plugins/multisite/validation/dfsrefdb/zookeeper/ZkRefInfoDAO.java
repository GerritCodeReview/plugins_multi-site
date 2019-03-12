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

import static org.apache.zookeeper.CreateMode.PERSISTENT;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.PathAndBytesable;
import org.apache.curator.framework.api.transaction.CuratorOp;
import org.apache.curator.framework.api.transaction.CuratorTransactionResult;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

public class ZkRefInfoDAO {
  public static final String OBJECT_ID_PATH = "objectId";

  public static String pathFor(ZkRefInfo info) {
    return "/" + info.projectName() + "/" + info.refName();
  }

  public static String pathFor(String projectName, Ref ref) {
    return pathFor(projectName, ref.getName());
  }

  public static String pathFor(String projectName, String refName) {
    return "/" + projectName + "/" + refName;
  }

  private final CuratorFramework client;

  public ZkRefInfoDAO(CuratorFramework client) {
    this.client = client;
  }

  public Optional<ZkRefInfo> read(String projectName, String refName) throws Exception {
    final String rootPath = pathFor(projectName, refName);

    if (!exists(rootPath)) return Optional.empty();

    final Optional<ObjectId> objectId = readObjectIdAt(rootPath + "/" + OBJECT_ID_PATH);

    if (!(objectId.isPresent())) {
      throw new CorruptedZkStorageException(
          String.format(
              "Corrupted content for ref %s, missing some of the sub info, %s present: %b",
              refName, OBJECT_ID_PATH, objectId.isPresent()));
    }

    return Optional.of(new ZkRefInfo(projectName, refName, objectId.get()));
  }

  public void update(ZkRefInfo info) throws Exception {
    writeInTransaction(info, () -> client.transactionOp().setData());
  }

  public void create(ZkRefInfo info) throws Exception {
    client.createContainers(pathFor(info));
    writeInTransaction(info, () -> client.transactionOp().create().withMode(PERSISTENT));
  }

  private void writeInTransaction(
      ZkRefInfo info, Supplier<PathAndBytesable<CuratorOp>> writeOpBuilderSupplier)
      throws Exception {
    String commonPath = pathFor(info);
    final List<CuratorTransactionResult> curatorTransactionResults =
        client
            .transaction()
            .forOperations(
                writeOpBuilderSupplier
                    .get()
                    .forPath(commonPath + "/" + OBJECT_ID_PATH, writeObjectId(info.objectId())));

    for (CuratorTransactionResult result : curatorTransactionResults) {
      if (result.getError() != 0)
        throw new IOException(
            String.format(
                "Error with code %d trying to write path %s ",
                result.getError(), result.getForPath()));
    }
  }

  private boolean exists(String path) throws Exception {
    return client.checkExists().forPath(path) != null;
  }

  private Optional<ObjectId> readObjectIdAt(String path) throws Exception {
    return parseAt(path, ObjectId::fromRaw);
  }

  private byte[] writeObjectId(ObjectId value) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final DataOutputStream stream = new DataOutputStream(out);
    value.copyRawTo(stream);
    return out.toByteArray();
  }

  private <T> Optional<T> parseAt(String path, Function<byte[], T> parser) throws Exception {
    if (client.checkExists().forPath(path) == null) return Optional.empty();

    final byte[] bytesMaybe = client.getData().forPath(path);
    return Optional.ofNullable(bytesMaybe).map(parser);
  }

  static class CorruptedZkStorageException extends Exception {
    private static final long serialVersionUID = 1L;

    public CorruptedZkStorageException(String message) {
      super(message);
    }
  }
}
