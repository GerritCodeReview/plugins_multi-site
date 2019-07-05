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

package com.googlesource.gerrit.plugins.multisite.validation;

import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import com.googlesource.gerrit.plugins.replication.ReplicationPushFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.transport.RemoteRefUpdate;

@Singleton
public class MultisiteBeforeReplicationPushFilter implements ReplicationPushFilter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SharedRefDatabase sharedRefDb;

  @Inject
  public MultisiteBeforeReplicationPushFilter(SharedRefDatabase sharedRefDb) {
    this.sharedRefDb = sharedRefDb;
  }

  @Override
  public List<RemoteRefUpdate> filter(String projectName, List<RemoteRefUpdate> remoteUpdatesList) {
    return filterOutOfSyncRemoteRefUpdates(projectName, sharedRefDb, remoteUpdatesList);
  }

  public List<RemoteRefUpdate> filterOutOfSyncRemoteRefUpdates(
      String projectName, SharedRefDatabase sharedRefDb, Collection<RemoteRefUpdate> refUpdates) {
    return filterOutOfSyncRemoteRefUpdates(
            projectName, sharedRefDb, refUpdates.stream(), Function.identity())
        .collect(Collectors.toList());
  }

  private static <T> Stream<T> filterOutOfSyncRemoteRefUpdates(
      String projectName,
      SharedRefDatabase sharedRefDb,
      Stream<T> refUpdates,
      Function<T, RemoteRefUpdate> extractor) {
    logger.atInfo().log("Applying filter to ref updates base on SharedDB status");
    List<String> changeRefsToRemove = new ArrayList<>();
    List<T> upToDateRefs = new ArrayList<>();
    refUpdates.forEach(
        toExtract -> {
          try {
            final RemoteRefUpdate refUpdate = extractor.apply(toExtract);
            String srcRef = refUpdate.getSrcRef();
            logger.atFine().log(
                String.format("Replication - Checking if source ref is up to date %s", srcRef));
            final boolean mostRecentVersion =
                sharedRefDb.isUpToDate(
                    projectName, SharedRefDatabase.newRef(srcRef, refUpdate.getNewObjectId()));
            if (mostRecentVersion) {
              logger.atFine().log(
                  String.format(
                      "Replication - Replicating %s because it's up to date!", toExtract));
              upToDateRefs.add(toExtract);
            } else {
              logger.atWarning().log(
                  "Not replicating Ref %s because it's out of sync with shared DB",
                  SharedRefDatabase.newRef(srcRef, refUpdate.getExpectedOldObjectId()));
              if (srcRef.startsWith("refs/changes")) {
                String topLevelRef = getTopLevelRefName(srcRef);
                logger.atWarning().log(
                    "Handling change refs that contain immutable refs that are not stored in"
                        + " Shared RefDb. These need to be removed via second filter. "
                        + "Refs to be removed %s, original ref %s",
                    topLevelRef, srcRef);

                changeRefsToRemove.add(topLevelRef);
              }
            }
          } catch (Exception e) {
            logger.atWarning().log(
                "Failed checking last ZK Ref version with exception %s", e.getMessage());
          }
        });
    logger.atInfo().log(
        "First filter applied resulting in %s. Will now filter 'Changes' remaining Refs %s",
        upToDateRefs, changeRefsToRemove);
    // Second filter to remove immutable Change Refs
    return upToDateRefs.stream()
        .filter(
            entry ->
                !changeRefsToRemove.contains(
                    getTopLevelRefName(extractor.apply(entry).getRemoteName())));
  }

  private static String getTopLevelRefName(String srcRef) {
    String[] srcRefSplit = srcRef.split("/");
    String topLevelRef = "";
    for (int i = 0; i <= srcRefSplit.length - 2; ++i) {
      topLevelRef += srcRefSplit[i] + "/";
    }
    return topLevelRef;
  }
}
