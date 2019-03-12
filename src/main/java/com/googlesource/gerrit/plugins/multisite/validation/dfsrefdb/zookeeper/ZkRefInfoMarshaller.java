package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.apache.commons.lang3.Validate;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.PathAndBytesable;
import org.apache.curator.framework.api.transaction.CuratorOp;
import org.apache.curator.framework.api.transaction.CuratorTransactionResult;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

import static org.apache.zookeeper.CreateMode.PERSISTENT;

public class ZkRefInfoMarshaller {
    public static final String LAST_UPDATED_BY_INSTANCE_PATH = "lastUpdatedByInstance";
    public static final String LAST_UPDATED_AT_PATH = "lastUpdatedAt";
    public static final String OBJECT_ID_PATH = "objectId";
    private final CuratorFramework client;

    public static String pathFor(ZkRefInfo info) {
        return info.projectName() + "/" + info.refName();
    }

    public static String pathFor(String projectName, Ref ref) {
        return pathFor(projectName, ref.getName());
    }

    public static String pathFor(String projectName, String refName) {
        return projectName + "/" + refName;
    }

    public ZkRefInfoMarshaller(CuratorFramework client) {
        this.client = client;
    }

    public Optional<ZkRefInfo> read(String projectName, String refName) throws Exception {
        if (!exists(refName))
            return Optional.empty();

        final Optional<ObjectId> objectId = readObjectIdAt(pathFor(projectName, refName) + "/" + OBJECT_ID_PATH);
        final Optional<Instant> lastUpdatedAt = readInstantAt(pathFor(projectName, refName) + "/" + LAST_UPDATED_AT_PATH);
        final Optional<UUID> lastUpdatedByInstance = readUuiddAt(pathFor(projectName, refName) + "/" + LAST_UPDATED_BY_INSTANCE_PATH);

        Validate.isTrue(objectId.isPresent() && lastUpdatedAt.isPresent() && lastUpdatedByInstance.isPresent(),
                "Corrupted content for ref %s, missing some of the sub info, %s present: %b, %s: %b, %s: %b",
                refName,
                OBJECT_ID_PATH, objectId.isPresent(),
                LAST_UPDATED_AT_PATH, lastUpdatedAt.isPresent(),
                LAST_UPDATED_BY_INSTANCE_PATH, lastUpdatedByInstance.isPresent());

        return Optional.of(new ZkRefInfo(projectName, refName, objectId.get(), lastUpdatedAt.get(),
                lastUpdatedByInstance.get()));
    }
    
    public void update(ZkRefInfo info) throws Exception {
        writeInTransaction(info, client.transactionOp().setData());
    }

    public void create(ZkRefInfo info) throws Exception {
        writeInTransaction(info, client.transactionOp().create().withMode(PERSISTENT));
    }

    private void writeInTransaction(ZkRefInfo info, PathAndBytesable<CuratorOp> writeOpBuilder) throws Exception {
        final List<CuratorTransactionResult> curatorTransactionResults = client.transaction().forOperations(
                writeOpBuilder.forPath(pathFor(info) + "/" + OBJECT_ID_PATH, writeObjectId(info.objectId())),
                writeOpBuilder.forPath(pathFor(info) + "/" + LAST_UPDATED_AT_PATH, writeInstant(info.lastUpdatedAt())),
                writeOpBuilder.forPath(pathFor(info) + "/" + LAST_UPDATED_BY_INSTANCE_PATH, writeUuid(info.lastWriterInstanceId()))
        );

        for (CuratorTransactionResult result : curatorTransactionResults) {
            if (result.getError() != 0)
                throw new IOException(String.format("Error with code %d trying to write path %s ", result.getError(), result.getForPath()));
        }
    }


    boolean exists(String path) throws Exception {
        return client.checkExists().forPath(path) != null;
    }

    Optional<Instant> readInstantAt(String path) throws Exception {
        return parseAt(path, bytes -> {
            final long epochMillis = ByteBuffer.wrap(bytes).getLong();
            return Instant.ofEpochMilli(epochMillis);
        });
    }

    Optional<ObjectId> readObjectIdAt(String path) throws Exception {
        return parseAt(path, ObjectId::fromRaw);
    }

    Optional<UUID> readUuiddAt(String path) throws Exception {
        return parseAt(path, bytes -> {
                    final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
                    return new UUID(byteBuffer.getLong(), byteBuffer.getLong());
                }
        );
    }

    byte[] writeInstant(Instant value) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final DataOutputStream stream = new DataOutputStream(out);
        stream.writeLong(value.toEpochMilli());
        return out.toByteArray();
    }

    byte[] writeObjectId(ObjectId value) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final DataOutputStream stream = new DataOutputStream(out);
        value.copyRawTo(stream);
        return out.toByteArray();
    }

    byte[] writeUuid(UUID value) throws IOException {
        final ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    private <T> Optional<T> parseAt(String path, Function<byte[], T> parser) throws Exception {
        final byte[] bytesMaybe = client.getData().forPath(path);
        return Optional.ofNullable(bytesMaybe).map(parser);
    }
}
