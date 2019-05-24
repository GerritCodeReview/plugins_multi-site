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

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.attributes.AttributesNodeProvider;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.events.ListenerList;
import org.eclipse.jgit.events.RepositoryEvent;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.RebaseTodoLine;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FS;

public class MultiSiteRepository extends Repository {

  private final Repository repository;
  private final RefDatabase refDatabase;
  private final MultiSiteRefDatabase multiSiteRefDatabase;

  public interface Factory {
    public MultiSiteRepository create(String projectName, Repository repository);
  }

  @Inject
  public MultiSiteRepository(
      MultiSiteRefDatabase.Factory multiSiteRefDbFactory,
      @Assisted String projectName,
      @Assisted Repository repository) {
    super(new BaseRepositoryBuilder());
    this.repository = repository;
    this.refDatabase = repository.getRefDatabase();
    this.multiSiteRefDatabase = multiSiteRefDbFactory.create(projectName, refDatabase);
  }

  @Override
  public void create(boolean b) throws IOException {}

  @Override
  public ObjectDatabase getObjectDatabase() {
    return repository.getObjectDatabase();
  }

  @Override
  public RefDatabase getRefDatabase() {
    return multiSiteRefDatabase;
  }

  @Override
  public StoredConfig getConfig() {
    return repository.getConfig();
  }

  @Override
  public AttributesNodeProvider createAttributesNodeProvider() {
    return repository.createAttributesNodeProvider();
  }

  @Override
  public void scanForRepoChanges() throws IOException {
    repository.scanForRepoChanges();
  }

  @Override
  public void notifyIndexChanged(boolean b) {
    repository.notifyIndexChanged(b);
  }

  @Override
  public ReflogReader getReflogReader(String s) throws IOException {
    return repository.getReflogReader(s);
  }

  @Override
  public int hashCode() {
    return repository.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return repository.equals(obj);
  }

  @Override
  public ListenerList getListenerList() {
    return repository.getListenerList();
  }

  @Override
  public void fireEvent(RepositoryEvent<?> event) {
    repository.fireEvent(event);
  }

  @Override
  public void create() throws IOException {
    repository.create();
  }

  @Override
  public File getDirectory() {
    return repository.getDirectory();
  }

  @Override
  public ObjectInserter newObjectInserter() {
    return repository.newObjectInserter();
  }

  @Override
  public ObjectReader newObjectReader() {
    return repository.newObjectReader();
  }

  @Override
  public FS getFS() {
    return repository.getFS();
  }

  @Override
  public boolean hasObject(AnyObjectId objectId) {
    return repository.hasObject(objectId);
  }

  @Override
  public ObjectLoader open(AnyObjectId objectId) throws MissingObjectException, IOException {
    return repository.open(objectId);
  }

  @Override
  public ObjectLoader open(AnyObjectId objectId, int typeHint)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    return repository.open(objectId, typeHint);
  }

  @Override
  public RefUpdate updateRef(String ref) throws IOException {
    return multiSiteRefDatabase.wrapRefUpdate(repository.updateRef(ref));
  }

  @Override
  public RefUpdate updateRef(String ref, boolean detach) throws IOException {
    return multiSiteRefDatabase.wrapRefUpdate(repository.updateRef(ref, detach));
  }

  @Override
  public RefRename renameRef(String fromRef, String toRef) throws IOException {
    return repository.renameRef(fromRef, toRef);
  }

  @Override
  public ObjectId resolve(String revstr)
      throws AmbiguousObjectException, IncorrectObjectTypeException, RevisionSyntaxException,
          IOException {
    return repository.resolve(revstr);
  }

  @Override
  public String simplify(String revstr) throws AmbiguousObjectException, IOException {
    return repository.simplify(revstr);
  }

  @Override
  public void incrementOpen() {
    repository.incrementOpen();
  }

  @Override
  public void close() {
    repository.close();
  }

  @Override
  public String toString() {
    return repository.toString();
  }

  @Override
  public String getFullBranch() throws IOException {
    return repository.getFullBranch();
  }

  @Override
  public String getBranch() throws IOException {
    return repository.getBranch();
  }

  @Override
  public Set<ObjectId> getAdditionalHaves() {
    return repository.getAdditionalHaves();
  }

  @Override
  public Map<String, Ref> getAllRefs() {
    return repository.getAllRefs();
  }

  @Override
  public Map<String, Ref> getTags() {
    return repository.getTags();
  }

  @Override
  public Ref peel(Ref ref) {
    return repository.peel(ref);
  }

  @Override
  public Map<AnyObjectId, Set<Ref>> getAllRefsByPeeledObjectId() {
    return repository.getAllRefsByPeeledObjectId();
  }

  @Override
  public File getIndexFile() throws NoWorkTreeException {
    return repository.getIndexFile();
  }

  @Override
  public RevCommit parseCommit(AnyObjectId id)
      throws IncorrectObjectTypeException, IOException, MissingObjectException {
    return repository.parseCommit(id);
  }

  @Override
  public DirCache readDirCache() throws NoWorkTreeException, CorruptObjectException, IOException {
    return repository.readDirCache();
  }

  @Override
  public DirCache lockDirCache() throws NoWorkTreeException, CorruptObjectException, IOException {
    return repository.lockDirCache();
  }

  @Override
  public RepositoryState getRepositoryState() {
    return repository.getRepositoryState();
  }

  @Override
  public boolean isBare() {
    return repository.isBare();
  }

  @Override
  public File getWorkTree() throws NoWorkTreeException {
    return repository.getWorkTree();
  }

  @Override
  public String shortenRemoteBranchName(String refName) {
    return repository.shortenRemoteBranchName(refName);
  }

  @Override
  public String getRemoteName(String refName) {
    return repository.getRemoteName(refName);
  }

  @Override
  public String getGitwebDescription() throws IOException {
    return repository.getGitwebDescription();
  }

  @Override
  public void setGitwebDescription(String description) throws IOException {
    repository.setGitwebDescription(description);
  }

  @Override
  public String readMergeCommitMsg() throws IOException, NoWorkTreeException {
    return repository.readMergeCommitMsg();
  }

  @Override
  public void writeMergeCommitMsg(String msg) throws IOException {
    repository.writeMergeCommitMsg(msg);
  }

  @Override
  public String readCommitEditMsg() throws IOException, NoWorkTreeException {
    return repository.readCommitEditMsg();
  }

  @Override
  public void writeCommitEditMsg(String msg) throws IOException {
    repository.writeCommitEditMsg(msg);
  }

  @Override
  public List<ObjectId> readMergeHeads() throws IOException, NoWorkTreeException {
    return repository.readMergeHeads();
  }

  @Override
  public void writeMergeHeads(List<? extends ObjectId> heads) throws IOException {
    repository.writeMergeHeads(heads);
  }

  @Override
  public ObjectId readCherryPickHead() throws IOException, NoWorkTreeException {
    return repository.readCherryPickHead();
  }

  @Override
  public ObjectId readRevertHead() throws IOException, NoWorkTreeException {
    return repository.readRevertHead();
  }

  @Override
  public void writeCherryPickHead(ObjectId head) throws IOException {
    repository.writeCherryPickHead(head);
  }

  @Override
  public void writeRevertHead(ObjectId head) throws IOException {
    repository.writeRevertHead(head);
  }

  @Override
  public void writeOrigHead(ObjectId head) throws IOException {
    repository.writeOrigHead(head);
  }

  @Override
  public ObjectId readOrigHead() throws IOException, NoWorkTreeException {
    return repository.readOrigHead();
  }

  @Override
  public String readSquashCommitMsg() throws IOException {
    return repository.readSquashCommitMsg();
  }

  @Override
  public void writeSquashCommitMsg(String msg) throws IOException {
    repository.writeSquashCommitMsg(msg);
  }

  @Override
  public List<RebaseTodoLine> readRebaseTodo(String path, boolean includeComments)
      throws IOException {
    return repository.readRebaseTodo(path, includeComments);
  }

  @Override
  public void writeRebaseTodoFile(String path, List<RebaseTodoLine> steps, boolean append)
      throws IOException {
    repository.writeRebaseTodoFile(path, steps, append);
  }

  @Override
  public Set<String> getRemoteNames() {
    return repository.getRemoteNames();
  }

  @Override
  public void autoGC(ProgressMonitor monitor) {
    repository.autoGC(monitor);
  }
}
