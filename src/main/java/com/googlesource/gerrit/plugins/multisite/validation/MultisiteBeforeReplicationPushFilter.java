package com.googlesource.gerrit.plugins.multisite.validation;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import com.googlesource.gerrit.plugins.replication.BeforeReplicationPushFilter;
import java.util.List;
import org.eclipse.jgit.transport.RemoteRefUpdate;

@Singleton
public class MultisiteBeforeReplicationPushFilter implements BeforeReplicationPushFilter {
  private final SharedRefDatabase sharedRefDb;

  @Inject
  public MultisiteBeforeReplicationPushFilter(SharedRefDatabase sharedRefDb) {
    this.sharedRefDb = sharedRefDb;
  }

  @Override
  public List<RemoteRefUpdate> filter(
      String projectName, List<RemoteRefUpdate> remoteUpdatesList) {
    return SharedRefDbValidation.filterOutOfSyncRemoteRefUpdates(
        projectName, sharedRefDb, remoteUpdatesList);
  }
}
