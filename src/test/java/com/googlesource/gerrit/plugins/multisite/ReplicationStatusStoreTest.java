package com.googlesource.gerrit.plugins.multisite;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Optional.empty;
import static java.util.Optional.of;

import java.time.Instant;
import org.junit.Test;

public class ReplicationStatusStoreTest {

  @Test
  public void shouldUpdateGlobalStatus() {
    Instant instant = Instant.now();
    Long nowish = instant.toEpochMilli();
    ReplicationStatusStore replicationStatusStore = new ReplicationStatusStore();

    replicationStatusStore.updateLastReplicationTime("myProject", nowish);

    assertThat(replicationStatusStore.getGlobalLastReplicationTime()).isEqualTo(nowish);
  }

  @Test
  public void shouldUpdateProjectStatus() {
    String projectName = "myProject";
    Instant instant = Instant.now();
    Long nowish = instant.toEpochMilli();
    ReplicationStatusStore replicationStatusStore = new ReplicationStatusStore();

    replicationStatusStore.updateLastReplicationTime(projectName, nowish);

    assertThat(replicationStatusStore.getLastReplicationTime(projectName)).isEqualTo(of(nowish));
  }

  @Test
  public void shouldNotReturnProjectStatus() {
    ReplicationStatusStore replicationStatusStore = new ReplicationStatusStore();

    assertThat(replicationStatusStore.getLastReplicationTime("nonExistentProject"))
        .isEqualTo(empty());
  }
}
