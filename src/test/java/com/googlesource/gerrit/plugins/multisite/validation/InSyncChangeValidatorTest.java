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

import java.io.IOException;
import java.util.List;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.events.RefReceivedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.validators.ValidationException;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Type;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static com.google.common.truth.Truth.assertThat;
import static com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase.NULL_REF;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

@RunWith(MockitoJUnitRunner.class)
public class InSyncChangeValidatorTest {
  static final String PROJECT_NAME = "AProject";
  static final Project.NameKey PROJECT_NAMEKEY = new Project.NameKey(PROJECT_NAME);
  static final String REF_NAME = "refs/heads/master";
  static final String REF_PATCHSET_NAME = "refs/changes/45/1245/1";
  static final ObjectId REF_OBJID = ObjectId.fromString("f2ffe80abb77223f3f8921f3f068b0e32d40f798");
  static final ObjectId REF_OBJID_OLD =
      ObjectId.fromString("a9a7a6fd1e9ad39a13fef5e897dc6d932a3282e1");
  static final ReceiveCommand RECEIVE_COMMAND_CREATE_REF =
      new ReceiveCommand(ObjectId.zeroId(), REF_OBJID, REF_NAME, Type.CREATE);
  static final ReceiveCommand RECEIVE_COMMAND_UPDATE_REF =
      new ReceiveCommand(REF_OBJID_OLD, REF_OBJID, REF_NAME, Type.UPDATE);
  static final ReceiveCommand RECEIVE_COMMAND_DELETE_REF =
      new ReceiveCommand(REF_OBJID_OLD, ObjectId.zeroId(), REF_NAME, Type.DELETE);
  static final ReceiveCommand RECEIVE_COMMAND_CREATE_PATCHSET_REF =
      new ReceiveCommand(ObjectId.zeroId(), REF_OBJID, REF_PATCHSET_NAME, Type.CREATE);

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Mock SharedRefDatabase dfsRefDatabase;

  @Mock Repository repo;

  @Mock RefDatabase localRefDatabase;

  @Mock GitRepositoryManager repoManager;

  private InSyncChangeValidator validator;

  static class TestRef implements Ref {
    private final String name;
    private final ObjectId objectId;

    public TestRef(String name, ObjectId objectId) {
      super();
      this.name = name;
      this.objectId = objectId;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public boolean isSymbolic() {
      return false;
    }

    @Override
    public Ref getLeaf() {
      return null;
    }

    @Override
    public Ref getTarget() {
      return null;
    }

    @Override
    public ObjectId getObjectId() {
      return objectId;
    }

    @Override
    public ObjectId getPeeledObjectId() {
      return null;
    }

    @Override
    public boolean isPeeled() {
      return false;
    }

    @Override
    public Storage getStorage() {
      return Storage.LOOSE;
    }
  }

  static class RefMatcher implements ArgumentMatcher<Ref> {
    private final String name;
    private final ObjectId objectId;

    public RefMatcher(String name, ObjectId objectId) {
      super();
      this.name = name;
      this.objectId = objectId;
    }

    @Override
    public boolean matches(Ref that) {
      if (that == null) {
        return false;
      }

      return name.equals(that.getName()) && objectId.equals(that.getObjectId());
    }
  }

  public static Ref eqRef(String name, ObjectId objectId) {
    return argThat(new RefMatcher(name, objectId));
  }

  Ref testRef = new TestRef(REF_NAME, REF_OBJID);
  RefReceivedEvent testRefReceivedEvent =
      new RefReceivedEvent() {

        @Override
        public String getRefName() {
          return command.getRefName();
        }

        @Override
        public com.google.gerrit.reviewdb.client.Project.NameKey getProjectNameKey() {
          return PROJECT_NAMEKEY;
        }
      };

  @Before
  public void setUp() throws IOException {
    doReturn(testRef).when(dfsRefDatabase).newRef(REF_NAME, REF_OBJID);
    doReturn(repo).when(repoManager).openRepository(PROJECT_NAMEKEY);
    doReturn(localRefDatabase).when(repo).getRefDatabase();
    doThrow(new NullPointerException("oldRef is null"))
        .when(dfsRefDatabase)
        .compareAndPut(any(), eq(null), any());
    doThrow(new NullPointerException("newRef is null"))
        .when(dfsRefDatabase)
        .compareAndPut(any(), any(), eq(null));
    doThrow(new NullPointerException("project name is null"))
        .when(dfsRefDatabase)
        .compareAndPut(eq(null), any(), any());
    //    doReturn(false).when(dfsRefDatabase).compareAndPut(eq(PROJECT_NAME), eqRef(REF_NAME,
    // REF_OBJID_OLD), eqRef(REF_NAME, REF_OBJID));

    validator = new InSyncChangeValidator(dfsRefDatabase, repoManager);
    repoManager.createRepository(PROJECT_NAMEKEY);
  }

  @Test
  public void shouldNotVerifyStatusOfImmutablePatchSetRefs() throws Exception {
    testRefReceivedEvent.command = RECEIVE_COMMAND_CREATE_PATCHSET_REF;
    final List<ValidationMessage> validationMessages =
        validator.onRefOperation(testRefReceivedEvent);

    assertThat(validationMessages).isEmpty();

    verifyZeroInteractions(dfsRefDatabase);
  }

  @Test
  public void shouldInsertNewRefInDfsDatabaseWhenHandlingRefCreationEvents() throws Exception {
    testRefReceivedEvent.command = RECEIVE_COMMAND_CREATE_REF;

    final List<ValidationMessage> validationMessages =
        validator.onRefOperation(testRefReceivedEvent);

    assertThat(validationMessages).isEmpty();
    verify(dfsRefDatabase)
        .compareAndPut(eq(PROJECT_NAME), eq(NULL_REF), eqRef(REF_NAME, REF_OBJID));
  }

  @Test
  public void shouldFailRefCreationIfInsertANewRefInDfsDatabaseFails() throws Exception {
    testRefReceivedEvent.command = RECEIVE_COMMAND_CREATE_REF;

    IllegalArgumentException alreadyInDb = new IllegalArgumentException("obj is already in db");

    doThrow(alreadyInDb)
        .when(dfsRefDatabase)
        .compareAndPut(eq(PROJECT_NAME), eq(NULL_REF), eqRef(REF_NAME, REF_OBJID));

    expectedException.expect(ValidationException.class);
    expectedException.expectCause(sameInstance(alreadyInDb));

    validator.onRefOperation(testRefReceivedEvent);
  }

  @Test
  public void shouldUpdateRefInDfsDatabaseWhenHandlingRefUpdateEvents() throws Exception {
    testRefReceivedEvent.command = RECEIVE_COMMAND_UPDATE_REF;
    doReturn(new TestRef(REF_NAME, REF_OBJID_OLD)).when(localRefDatabase).getRef(REF_NAME);
    doReturn(true).when(dfsRefDatabase).compareAndPut(
            eq(PROJECT_NAME), eqRef(REF_NAME, REF_OBJID_OLD), eqRef(REF_NAME, REF_OBJID));

    final List<ValidationMessage> validationMessages =
        validator.onRefOperation(testRefReceivedEvent);

    assertThat(validationMessages).isEmpty();
    verify(dfsRefDatabase)
        .compareAndPut(
            eq(PROJECT_NAME), eqRef(REF_NAME, REF_OBJID_OLD), eqRef(REF_NAME, REF_OBJID));
  }

  @Test
  public void shouldFailRefUpdateIfRefUpdateInDfsRefDatabaseReturnsFalse() throws Exception {
    testRefReceivedEvent.command = RECEIVE_COMMAND_UPDATE_REF;
    doReturn(new TestRef(REF_NAME, REF_OBJID_OLD)).when(localRefDatabase).getRef(REF_NAME);
    doReturn(false).when(dfsRefDatabase).compareAndPut(
            eq(PROJECT_NAME), eqRef(REF_NAME, REF_OBJID_OLD), eqRef(REF_NAME, REF_OBJID));
    expectedException.expect(ValidationException.class);
    expectedException.expectCause(nullValue(Exception.class));

    validator.onRefOperation(testRefReceivedEvent);
  }

  @Test
  public void shouldFailRefUpdateIfRefIsNotInDfsRefDatabase() throws Exception {
    testRefReceivedEvent.command = RECEIVE_COMMAND_UPDATE_REF;
    doReturn(null).when(localRefDatabase).getRef(REF_NAME);

    expectedException.expect(ValidationException.class);
    expectedException.expectCause(nullValue(Exception.class));

    validator.onRefOperation(testRefReceivedEvent);
  }

  @Test
  public void shouldDeleteRefInDfsDatabaseWhenHandlingRefDeleteEvents() throws Exception {
    testRefReceivedEvent.command = RECEIVE_COMMAND_DELETE_REF;
    doReturn(new TestRef(REF_NAME, REF_OBJID_OLD)).when(localRefDatabase).getRef(REF_NAME);
    doReturn(true).when(dfsRefDatabase).compareAndRemove(eq(PROJECT_NAME), eqRef(REF_NAME, REF_OBJID_OLD));

    final List<ValidationMessage> validationMessages = validator.onRefOperation(testRefReceivedEvent);

    assertThat(validationMessages).isEmpty();

    verify(dfsRefDatabase).compareAndRemove(eq(PROJECT_NAME), eqRef(REF_NAME, REF_OBJID_OLD));
  }

    @Test
    public void shouldFailRefDeletionIfRefDeletionInDfsRefDatabaseReturnsFalse()
        throws Exception {
      testRefReceivedEvent.command = RECEIVE_COMMAND_DELETE_REF;
      doReturn(new TestRef(REF_NAME, REF_OBJID_OLD)).when(localRefDatabase).getRef(REF_NAME);
      doReturn(false).when(dfsRefDatabase).compareAndRemove(eq(PROJECT_NAME), eqRef(REF_NAME, REF_OBJID_OLD));

      expectedException.expect(ValidationException.class);
      expectedException.expectCause(nullValue(Exception.class));

     validator.onRefOperation(testRefReceivedEvent);
    }

    @Test
    public void shouldFailRefDeletionIfRefIsNotInDfsDatabase() throws Exception {
      testRefReceivedEvent.command = RECEIVE_COMMAND_DELETE_REF;
      doReturn(null).when(localRefDatabase).getRef(REF_NAME);

      expectedException.expect(ValidationException.class);
      expectedException.expectCause(nullValue(Exception.class));

      validator.onRefOperation(testRefReceivedEvent);
    }
}
