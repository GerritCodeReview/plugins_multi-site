package com.googlesource.gerrit.plugins.multisite.validation;

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportProtocol;
import org.eclipse.jgit.transport.URIish;

public class MultiSiteTransportProtocol extends TransportProtocol {

  private final MultiSiteTransport.Factory multisiteTranportFactory;
  private final Provider<TransportProtocol> baseTransportProtocol;

  @Inject
  MultiSiteTransportProtocol(
      MultiSiteTransport.Factory multisiteTranportFactory,
      Provider<TransportProtocol> transportProtocol) {
    this.multisiteTranportFactory = multisiteTranportFactory;
    this.baseTransportProtocol = transportProtocol;
  }

  @Override
  public String getName() {
    return "MultiSiteProtocol";
  }

  @Override
  public Transport open(URIish uri, Repository local, String remoteName)
      throws NotSupportedException, TransportException {
    Transport transport = baseTransportProtocol.get().open(uri, local, remoteName);

    return multisiteTranportFactory.create(uri, local, remoteName, transport);
  }
}
