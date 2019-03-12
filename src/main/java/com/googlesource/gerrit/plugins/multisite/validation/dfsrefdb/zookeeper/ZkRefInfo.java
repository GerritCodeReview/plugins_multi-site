package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

public class ZkRefInfo {

    private final String refName;
    private final String projectName;
    private final ObjectId objectId;
    private final UUID lastWriterInstanceId;
    private final Instant lastUpdatedAt;

    public ZkRefInfo(final String projectName, final String refName, final ObjectId objectId, final Instant lastUpdatedAt,
                     final UUID lastWriterInstanceId) {
        this.projectName = projectName;
        this.objectId = objectId;
        this.lastWriterInstanceId = lastWriterInstanceId;
        this.refName = refName;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public String refName() {
        return refName;
    }

    public String projectName() {
        return projectName;
    }

    public ObjectId objectId() {
        return objectId;
    }

    public UUID lastWriterInstanceId() {
        return lastWriterInstanceId;
    }

    public Instant lastUpdatedAt() {
        return lastUpdatedAt;
    }


    public static ZkRefInfo newInstance(final String projectName, final Ref ref, final UUID instanceId) {
        return new ZkRefInfo(projectName, ref.getName(), ref.getObjectId(), Instant.now(), instanceId);
    }

}
