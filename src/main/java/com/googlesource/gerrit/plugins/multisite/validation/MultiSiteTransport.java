package com.googlesource.gerrit.plugins.multisite.validation;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.PushConnection;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;

public class MultiSiteTransport extends Transport {

  private final MultiSitePushConnection.Factory multisitePushConnection;
  private final SharedRefDatabase sharedRefDb;
  private Transport baseTransport;

  public interface Factory {
    MultiSiteTransport create(URIish uri, Repository repo, String remoteName, Transport transport);
  }

  @Inject
  MultiSiteTransport(
      @Assisted Repository repo,
      @Assisted URIish uri,
      @Assisted String remoteName,
      @Assisted Transport transport,
      SharedRefDatabase sharedRefDb,
      MultiSitePushConnection.Factory multisitePushConnection) {
    super(repo, uri);
    this.sharedRefDb = sharedRefDb;
    this.multisitePushConnection = multisitePushConnection;
    this.baseTransport = transport;
  }

  @Override
  public FetchConnection openFetch() throws NotSupportedException, TransportException {
    return baseTransport.openFetch();
  }

  @Override
  public PushConnection openPush() throws NotSupportedException, TransportException {
    PushConnection pushConnection = baseTransport.openPush();
    return multisitePushConnection.create(sharedRefDb, pushConnection);
  }

  @Override
  public void close() {
    baseTransport.close();
  }
}
