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

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushConnection;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefLeaseSpec;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;

public class MultiSiteTransport extends Transport {

  private final MultiSitePushConnection.Factory multiSitePushConnection;

  private final SharedRefDatabase sharedRefDb;
  private final Transport baseTransport;
  private final MultiSiteRepository repo;

  public interface Factory {
    MultiSiteTransport create(MultiSiteRepository repo, URIish uri, Transport transport);
  }

  @Inject
  MultiSiteTransport(
      @Assisted MultiSiteRepository repo,
      @Assisted URIish uri,
      @Assisted Transport transport,
      SharedRefDatabase sharedRefDb,
      MultiSitePushConnection.Factory multiSitePushConnection) {
    super(repo, uri);
    this.sharedRefDb = sharedRefDb;
    this.multiSitePushConnection = multiSitePushConnection;
    this.baseTransport = transport;
    this.repo = repo;
  }

  @Override
  public FetchConnection openFetch() throws NotSupportedException, TransportException {
    return baseTransport.openFetch();
  }

  @Override
  public PushConnection openPush() throws NotSupportedException, TransportException {
    PushConnection pushConnection = baseTransport.openPush();
    return multiSitePushConnection.create(sharedRefDb, pushConnection, repo.getProjectName());
  }

  @Override
  public void close() {
    baseTransport.close();
  }

  @Override
  public URIish getURI() {
    return baseTransport.getURI();
  }

  @Override
  public String getOptionUploadPack() {
    return baseTransport.getOptionUploadPack();
  }

  @Override
  public void setOptionUploadPack(String where) {
    baseTransport.setOptionUploadPack(where);
  }

  @Override
  public TagOpt getTagOpt() {
    return baseTransport.getTagOpt();
  }

  @Override
  public void setTagOpt(TagOpt option) {
    baseTransport.setTagOpt(option);
  }

  @Override
  public boolean isFetchThin() {
    return baseTransport.isFetchThin();
  }

  @Override
  public void setFetchThin(boolean fetchThin) {
    baseTransport.setFetchThin(fetchThin);
  }

  @Override
  public boolean isCheckFetchedObjects() {
    return baseTransport.isCheckFetchedObjects();
  }

  @Override
  public void setCheckFetchedObjects(boolean check) {
    baseTransport.setCheckFetchedObjects(check);
  }

  @Override
  public ObjectChecker getObjectChecker() {
    return baseTransport.getObjectChecker();
  }

  @Override
  public void setObjectChecker(ObjectChecker impl) {
    baseTransport.setObjectChecker(impl);
  }

  @Override
  public String getOptionReceivePack() {
    return baseTransport.getOptionReceivePack();
  }

  @Override
  public void setOptionReceivePack(String optionReceivePack) {
    baseTransport.setOptionReceivePack(optionReceivePack);
  }

  @Override
  public boolean isPushThin() {
    return baseTransport.isPushThin();
  }

  @Override
  public void setPushThin(boolean pushThin) {
    baseTransport.setPushThin(pushThin);
  }

  @Override
  public boolean isPushAtomic() {
    return baseTransport.isPushAtomic();
  }

  @Override
  public void setPushAtomic(boolean atomic) {
    baseTransport.setPushAtomic(atomic);
  }

  @Override
  public boolean isRemoveDeletedRefs() {
    return baseTransport.isRemoveDeletedRefs();
  }

  @Override
  public void setRemoveDeletedRefs(boolean remove) {
    baseTransport.setRemoveDeletedRefs(remove);
  }

  @Override
  public long getFilterBlobLimit() {
    return baseTransport.getFilterBlobLimit();
  }

  @Override
  public void setFilterBlobLimit(long bytes) {
    baseTransport.setFilterBlobLimit(bytes);
  }

  @Override
  public void applyConfig(RemoteConfig cfg) {
    baseTransport.applyConfig(cfg);
  }

  @Override
  public boolean isDryRun() {
    return baseTransport.isDryRun();
  }

  @Override
  public void setDryRun(boolean dryRun) {
    baseTransport.setDryRun(dryRun);
  }

  @Override
  public int getTimeout() {
    return baseTransport.getTimeout();
  }

  @Override
  public void setTimeout(int seconds) {
    baseTransport.setTimeout(seconds);
  }

  @Override
  public PackConfig getPackConfig() {
    return baseTransport.getPackConfig();
  }

  @Override
  public void setPackConfig(PackConfig pc) {
    baseTransport.setPackConfig(pc);
  }

  @Override
  public void setCredentialsProvider(CredentialsProvider credentialsProvider) {
    baseTransport.setCredentialsProvider(credentialsProvider);
  }

  @Override
  public CredentialsProvider getCredentialsProvider() {
    return baseTransport.getCredentialsProvider();
  }

  @Override
  public List<String> getPushOptions() {
    return baseTransport.getPushOptions();
  }

  @Override
  public void setPushOptions(List<String> pushOptions) {
    baseTransport.setPushOptions(pushOptions);
  }

  @Override
  public FetchResult fetch(ProgressMonitor monitor, Collection<RefSpec> toFetch)
      throws NotSupportedException, TransportException {
    return baseTransport.fetch(monitor, toFetch);
  }

  @Override
  public PushResult push(
      ProgressMonitor monitor, Collection<RemoteRefUpdate> toPush, OutputStream out)
      throws NotSupportedException, TransportException {
    Collection<RemoteRefUpdate> filteredToPush =
        SharedRefDbValidation.filterOutOfSyncRemoteRefUpdates(
            repo.getProjectName(), sharedRefDb, toPush);
    return baseTransport.push(monitor, filteredToPush, out);
  }

  @Override
  public PushResult push(ProgressMonitor monitor, Collection<RemoteRefUpdate> toPush)
      throws NotSupportedException, TransportException {
    Collection<RemoteRefUpdate> filteredToPush =
        SharedRefDbValidation.filterOutOfSyncRemoteRefUpdates(
            repo.getProjectName(), sharedRefDb, toPush);

    return baseTransport.push(monitor, filteredToPush);
  }

  @Override
  public Collection<RemoteRefUpdate> findRemoteRefUpdatesFor(Collection<RefSpec> specs)
      throws IOException {
    return baseTransport.findRemoteRefUpdatesFor(specs);
  }

  @Override
  public Collection<RemoteRefUpdate> findRemoteRefUpdatesFor(
      Collection<RefSpec> specs, Map<String, RefLeaseSpec> leases) throws IOException {
    return baseTransport.findRemoteRefUpdatesFor(specs, leases);
  }
}
