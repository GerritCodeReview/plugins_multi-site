package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb;

import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

public abstract class MultiSiteRefUpdate extends RefUpdate {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected final RefUpdate refUpdateBase;
  private final SharedRefDatabase sharedDb;

  @Inject
  public MultiSiteRefUpdate(SharedRefDatabase db, RefUpdate ref) {
    super(ref.getRef());
    refUpdateBase = ref;
    this.sharedDb = db;
  }

  private void checkSharedDBForRefUpdate() throws IOException {
    try {
      Ref newRef = sharedDb.newRef(refUpdateBase.getName(), refUpdateBase.getNewObjectId());

      if (!sharedDb.compareAndPut("ProjectName", refUpdateBase.getRef(), newRef)) {
        throw new IOException(
            String.format(
                "Unable to update ref '%s', the local objectId '%s' is not equal to the one "
                    + "in the shared ref datasuper",
                newRef.getName(), refUpdateBase.getName()));
      }
    } catch (IOException ioe) {
      logger.atSevere().withCause(ioe).log(
          "Local status inconsistent with shared ref datasuper for ref %s. "
              + "Trying to update it cannot extract the existing one on DB",
          refUpdateBase.getName());

      throw new IOException(
          String.format(
              "Unable to update ref '%s', cannot open the local ref on the local DB",
              refUpdateBase.getName()),
          ioe);
    }
  }

  private void checkSharedDBForRefCreate() throws IOException {
    try {
      Ref newRef = sharedDb.newRef(refUpdateBase.getName(), refUpdateBase.getNewObjectId());
      sharedDb.compareAndCreate("ProjectName", newRef);
    } catch (IOException alreadyInDB) {
      logger.atSevere().withCause(alreadyInDB).log(
          "Local status inconsistent with shared ref database for ref %s. "
              + "Trying to delete it but it is not in the DB",
          refUpdateBase.getName());

      throw new IOException(
          String.format(
              "Unable to update ref '%s', cannot find it in the shared ref database",
              refUpdateBase.getName()),
          alreadyInDB);
    }
  }

  private void checkSharedDbForRefDelete() throws IOException {
    Ref oldRef = this.getRef();
    try {
      if (!sharedDb.compareAndRemove("ProjectName", oldRef)) {
        throw new IOException(
            String.format(
                "Unable to delete ref '%s', the local ObjectId '%s' is not equal to the one "
                    + "in the shared ref database",
                oldRef.getName(), oldRef.getName()));
      }
    } catch (IOException ioe) {
      logger.atSevere().withCause(ioe).log(
          "Local status inconsistent with shared ref database for ref %s. "
              + "Trying to delete it but it is not in the DB",
          oldRef.getName());

      throw new IOException(
          String.format(
              "Unable to delete ref '%s', cannot find it in the shared ref database",
              oldRef.getName()),
          ioe);
    }
  }

  @Override
  protected abstract RefDatabase getRefDatabase();

  @Override
  protected abstract Repository getRepository();

  @Override
  protected abstract boolean tryLock(boolean deref) throws IOException;

  @Override
  protected abstract void unlock();

  @Override
  protected abstract Result doUpdate(Result result) throws IOException;

  @Override
  protected abstract Result doDelete(Result result) throws IOException;

  @Override
  protected abstract Result doLink(String target) throws IOException;

  @Override
  public Result update() throws IOException {
    checkSharedDBForRefUpdate();
    return refUpdateBase.update();
  }

  @Override
  public Result update(RevWalk rev) throws IOException {
    checkSharedDBForRefUpdate();
    return refUpdateBase.update(rev);
  }

  @Override
  public Result delete() throws IOException {
    checkSharedDbForRefDelete();
    return refUpdateBase.delete();
  }

  @Override
  public Result delete(RevWalk walk) throws IOException {
    checkSharedDbForRefDelete();
    return refUpdateBase.delete(walk);
  }
}
