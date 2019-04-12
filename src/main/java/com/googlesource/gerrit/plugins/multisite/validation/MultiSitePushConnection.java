package com.googlesource.gerrit.plugins.multisite.validation;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.PushConnection;
import org.eclipse.jgit.transport.RemoteRefUpdate;

public class MultiSitePushConnection implements PushConnection {

  private final PushConnection pushConnection;
  private final SharedRefDatabase sharedRefDb;
  private final String projectName;

  public interface Factory {
    MultiSitePushConnection create(
        SharedRefDatabase sharedRefDb, PushConnection pushConnection, String projectName);
  }

  @Inject
  MultiSitePushConnection(
      @Assisted SharedRefDatabase sharedRefDb,
      @Assisted PushConnection pushConnection,
      @Assisted String projectName) {
    this.pushConnection = pushConnection;
    this.sharedRefDb = sharedRefDb;
    this.projectName = projectName;
  }

  @Override
  public void push(ProgressMonitor monitor, Map<String, RemoteRefUpdate> refUpdates)
      throws TransportException {
    pushConnection.push(
        monitor,
        SharedRefDbValidation.filterOutOfSyncRemoteRefUpdates(
            projectName, sharedRefDb, refUpdates));
  }

  @Override
  public void push(
      ProgressMonitor monitor, Map<String, RemoteRefUpdate> refUpdates, OutputStream out)
      throws TransportException {
    pushConnection.push(
        monitor,
        SharedRefDbValidation.filterOutOfSyncRemoteRefUpdates(projectName, sharedRefDb, refUpdates),
        out);
  }

  @Override
  public Map<String, Ref> getRefsMap() {
    return pushConnection.getRefsMap();
  }

  @Override
  public Collection<Ref> getRefs() {
    return pushConnection.getRefs();
  }

  @Override
  public Ref getRef(String name) {
    return pushConnection.getRef(name);
  }

  @Override
  public void close() {
    pushConnection.close();
  }

  @Override
  public String getMessages() {
    return pushConnection.getMessages();
  }

  @Override
  public String getPeerUserAgent() {
    return pushConnection.getPeerUserAgent();
  }
}
