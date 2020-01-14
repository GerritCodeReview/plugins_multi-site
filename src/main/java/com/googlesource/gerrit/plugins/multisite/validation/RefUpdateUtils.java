package com.googlesource.gerrit.plugins.multisite.validation;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import java.time.Instant;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;

public class RefUpdateUtils {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String MULTI_SITE_VERSIONING_REF = "refs/multi-site/project-version";

  public static Optional<ReceiveCommand> getVersioningCommand(
      GitRepositoryManager gitRepositoryManager, String project) {
    ReceiveCommand receiveCommand;
    try {
      Repository repository = gitRepositoryManager.openRepository(Project.NameKey.parse(project));
      ObjectInserter ins = repository.newObjectInserter();
      ObjectId newId =
          ins.insert(OBJ_BLOB, Long.toString(Instant.now().toEpochMilli()).getBytes(UTF_8));
      Ref ref = repository.findRef(MULTI_SITE_VERSIONING_REF);
      ObjectId oldId = ref != null ? ref.getObjectId() : ObjectId.zeroId();
      receiveCommand = new ReceiveCommand(oldId, newId, MULTI_SITE_VERSIONING_REF);
      ins.flush();

      return Optional.of(receiveCommand);
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Cannot create versioning command");
      return Optional.empty();
    }
  }
}
