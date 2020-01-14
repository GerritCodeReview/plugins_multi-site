package com.googlesource.gerrit.plugins.multisite.validation;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.time.Instant;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

public class ProjectVersionRefUpdate implements GitReferenceUpdatedListener {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    GitRepositoryManager gitRepositoryManager;

    @Inject
    public ProjectVersionRefUpdate(GitRepositoryManager gitRepositoryManager) {
        this.gitRepositoryManager = gitRepositoryManager;
    }

    @Override
    public void onGitReferenceUpdated(Event event) {
        String projectName = event.getProjectName();
        logger.atInfo().log("Intercepted ref update " + event.getRefName() + " for project " + projectName);

        getVersioningRefUpdate(projectName);
    }

    public static final String MULTI_SITE_VERSIONING_REF = "refs/multi-site/project-version";
    public void getVersioningRefUpdate(String projectName) {

        Repository repository;
        try {
            repository = gitRepositoryManager.openRepository(Project.NameKey.parse(projectName));
        } catch (Exception e) {
            logger.atSevere().withCause(e).log("Cannot open repository " + projectName);
            return;
        }

        BatchRefUpdate bru = repository.getRefDatabase().newBatchUpdate();
        try {
            ObjectInserter ins = repository.newObjectInserter();
            ObjectId newId =
                    ins.insert(OBJ_BLOB, Long.toString(Instant.now().toEpochMilli()).getBytes(UTF_8));
            Ref ref = repository.findRef(MULTI_SITE_VERSIONING_REF);

            ObjectId oldId = ref != null ? ref.getObjectId() : ObjectId.zeroId();
            bru.addCommand(new ReceiveCommand(oldId, newId, MULTI_SITE_VERSIONING_REF));
            bru.setAllowNonFastForwards(true);
            ins.flush();

            execute(repository, bru);

        } catch (Exception e) {
            logger.atSevere().withCause(e).log("Cannot create versioning command");
            return;
        }
    }

    private void execute(Repository git, BatchRefUpdate bru) throws IOException {
        try (RevWalk rw = new RevWalk(git)) {
            bru.execute(rw, NullProgressMonitor.INSTANCE);
        }
        for (ReceiveCommand cmd : bru.getCommands()) {
            logger.atInfo().log("===> Result " + cmd.getResult().toString());
            if (cmd.getResult() != ReceiveCommand.Result.OK) {
                throw new IOException("Failed to update " + cmd.getRefName());
            }
        }
    }
}
