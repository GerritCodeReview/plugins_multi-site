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

  public static Map<String, RemoteRefUpdate> filterOutOfSyncRefUpdates(
      String projectName, SharedRefDatabase sharedRefDb, Map<String, RemoteRefUpdate> refUpdates) {
    return filterOutOfSyncRefUpdates(
            projectName, sharedRefDb, refUpdates.entrySet().stream(), entry -> entry.getValue())
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  public static Collection<RemoteRefUpdate> filterOutOfSyncRefUpdates(
      String projectName, SharedRefDatabase sharedRefDb, Collection<RemoteRefUpdate> refUpdates) {
    return filterOutOfSyncRefUpdates(
            projectName, sharedRefDb, refUpdates.stream(), Function.identity())
        .collect(Collectors.toList());
  }

  public static <T> Stream<T> filterOutOfSyncRefUpdates(
      String projectName,
      SharedRefDatabase sharedRefDb,
      Stream<T> refUpdates,
      Function<T, RemoteRefUpdate> extractor) {
    logger.atInfo().log("Applying filter to ref updates base on SharedDB status");
    List<T> result = new ArrayList<>();
    refUpdates.forEach(
        toExtract -> {
          try {
            final RemoteRefUpdate refUpdate = extractor.apply(toExtract);
            final boolean mostRecentVersion =
                sharedRefDb.isMostRecentVersion(
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
    logger.atInfo().log("Filter applied, returning %s references", result.size());
    return result.stream();
  }
}
