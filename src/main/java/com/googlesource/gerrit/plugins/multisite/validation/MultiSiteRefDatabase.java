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
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;

public class MultiSiteRefDatabase extends RefDatabase {
  private final MultiSiteRefUpdate.Factory refUpdateFactory;
  private final String projectName;
  private final RefDatabase refDatabase;

  public interface Factory {
    public MultiSiteRefDatabase create(String projectName, RefDatabase refDatabase);
  }

  @Inject
  public MultiSiteRefDatabase(
      MultiSiteRefUpdate.Factory refUpdateFactory,
      @Assisted String projectName,
      @Assisted RefDatabase refDatabase) {
    this.refUpdateFactory = refUpdateFactory;
    this.projectName = projectName;
    this.refDatabase = refDatabase;
  }

  @Override
  public int hashCode() {
    return refDatabase.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return refDatabase.equals(obj);
  }

  @Override
  public void create() throws IOException {
    refDatabase.create();
  }

  @Override
  public void close() {
    refDatabase.close();
  }

  @Override
  public boolean isNameConflicting(String name) throws IOException {
    return refDatabase.isNameConflicting(name);
  }

  @Override
  public Collection<String> getConflictingNames(String name) throws IOException {
    return refDatabase.getConflictingNames(name);
  }

  @Override
  public RefUpdate newUpdate(String name, boolean detach) throws IOException {
    return wrapRefUpdate(refDatabase.newUpdate(name, detach));
  }

  @Override
  public RefRename newRename(String fromName, String toName) throws IOException {
    return refDatabase.newRename(fromName, toName);
  }

  @Override
  public BatchRefUpdate newBatchUpdate() {
    return refDatabase.newBatchUpdate();
  }

  @Override
  public boolean performsAtomicTransactions() {
    return refDatabase.performsAtomicTransactions();
  }

  @Override
  public Ref getRef(String name) throws IOException {
    return refDatabase.getRef(name);
  }

  @Override
  public String toString() {
    return refDatabase.toString();
  }

  @Override
  public Ref exactRef(String name) throws IOException {
    return refDatabase.exactRef(name);
  }

  @Override
  public Map<String, Ref> exactRef(String... refs) throws IOException {
    return refDatabase.exactRef(refs);
  }

  @Override
  public Ref firstExactRef(String... refs) throws IOException {
    return refDatabase.firstExactRef(refs);
  }

  @Override
  public List<Ref> getRefs() throws IOException {
    return refDatabase.getRefs();
  }

  @SuppressWarnings("deprecation")
  @Override
  public Map<String, Ref> getRefs(String prefix) throws IOException {
    return refDatabase.getRefs(prefix);
  }

  @Override
  public List<Ref> getRefsByPrefix(String prefix) throws IOException {
    return refDatabase.getRefsByPrefix(prefix);
  }

  @Override
  public boolean hasRefs() throws IOException {
    return refDatabase.hasRefs();
  }

  @Override
  public List<Ref> getAdditionalRefs() throws IOException {
    return refDatabase.getAdditionalRefs();
  }

  @Override
  public Ref peel(Ref ref) throws IOException {
    return refDatabase.peel(ref);
  }

  @Override
  public void refresh() {
    refDatabase.refresh();
  }

  RefUpdate wrapRefUpdate(RefUpdate refUpdate) {
    return refUpdateFactory.create(projectName, refUpdate);
  }
}
