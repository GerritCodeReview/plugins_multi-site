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

package com.googlesource.gerrit.plugins.multisite.validation;

import com.google.common.flogger.FluentLogger;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.transport.RemoteRefUpdate;

public class SharedRefDbValidation {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static Map<String, RemoteRefUpdate> filterOutOfSyncRemoteRefUpdates(
      String projectName, SharedRefDatabase sharedRefDb, Map<String, RemoteRefUpdate> refUpdates) {
    return filterOutOfSyncRemoteRefUpdates(
            projectName, sharedRefDb, refUpdates.entrySet().stream(), entry -> entry.getValue())
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  public static Collection<RemoteRefUpdate> filterOutOfSyncRemoteRefUpdates(
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
    logger.atFine().log("Applying filter to ref updates base on SharedDB status");
    List<T> result = new ArrayList<>();
    refUpdates.forEach(
        toExtract -> {
          try {
            final RemoteRefUpdate refUpdate = extractor.apply(toExtract);
            final boolean mostRecentVersion =
                sharedRefDb.isMostRecentRefVersion(
                    projectName,
                    sharedRefDb.newRef(refUpdate.getSrcRef(), refUpdate.getNewObjectId()));
            if (mostRecentVersion) {
              result.add(toExtract);
            } else {
              logger.atWarning().log(
                  "Not replicating Ref %s because it'sout of sync with shared DB",
                  sharedRefDb.newRef(refUpdate.getSrcRef(), refUpdate.getExpectedOldObjectId()));
            }
          } catch (Exception e) {
            logger.atWarning().log(
                "Failed checking last ZK Ref version with exception %s", e.getMessage());
          }
        });
    logger.atFine().log("Filter applied, returning %s references", result.size());
    return result.stream();
  }
}
