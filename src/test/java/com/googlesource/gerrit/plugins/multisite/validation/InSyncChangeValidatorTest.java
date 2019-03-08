package com.googlesource.gerrit.plugins.multisite.validation;

import static com.google.common.truth.Truth.assertThat;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.events.RefReceivedEvent;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.validators.ValidationException;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DfsRefDatabase;
import java.util.List;
import java.util.NoSuchElementException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class InSyncChangeValidatorTest {
  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Mock DfsRefDatabase dfsRefDatabase;

  private InSyncChangeValidator validator;

  @Before
  public void setUp() {
    validator = new InSyncChangeValidator(dfsRefDatabase);
  }

  @Test
  public void shouldNotVerifyStatusOfImmutableRefs_ChangesRefs() throws ValidationException {
    final RefReceivedEvent refEvent =
        new RefReceivedEventBuilder().refName("refs/changes/97/187697/4").build();
    final List<ValidationMessage> validationMessages = validator.onRefOperation(refEvent);

    assertThat(validationMessages).isEmpty();

    verifyZeroInteractions(dfsRefDatabase);
  }

  @Test
  public void shouldInsertNewRefInDfsDatabaseWhenHandlingRefCreationEvents()
      throws ValidationException {
    final RefReceivedEvent refEvent = new RefReceivedEventBuilder().refCreation().build();
    final List<ValidationMessage> validationMessages = validator.onRefOperation(refEvent);

    assertThat(validationMessages).isEmpty();

    verify(dfsRefDatabase).createRef(refEvent.getRefName(), refEvent.command.getNewId());
  }

  @Test
  public void shouldFailRefCreationIfInsertANewRefInDfsDatabaseFails() throws ValidationException {
    final RefReceivedEvent refEvent = new RefReceivedEventBuilder().refCreation().build();

    IllegalArgumentException alreadyInDb = new IllegalArgumentException("obj is already in db");

    doThrow(alreadyInDb)
        .when(dfsRefDatabase)
        .createRef(refEvent.getRefName(), refEvent.command.getNewId());

    expectedException.expect(ValidationException.class);
    expectedException.expectCause(sameInstance(alreadyInDb));

    validator.onRefOperation(refEvent);
  }

  @Test
  public void shouldUpdateRefInDfsDatabaseWhenHandlingRefUpdateEvents() throws ValidationException {
    final RefReceivedEvent refEvent = new RefReceivedEventBuilder().build();

    when(dfsRefDatabase.updateRefId(
            refEvent.getRefName(), refEvent.command.getNewId(), refEvent.command.getOldId()))
        .thenReturn(true);

    final List<ValidationMessage> validationMessages = validator.onRefOperation(refEvent);

    assertThat(validationMessages).isEmpty();

    verify(dfsRefDatabase)
        .updateRefId(
            refEvent.getRefName(), refEvent.command.getNewId(), refEvent.command.getOldId());
  }

  @Test
  public void shouldFailRefUpdateIfRefUpdateInDfsRefDatabaseReturnsFalse()
      throws ValidationException {
    final RefReceivedEvent refEvent = new RefReceivedEventBuilder().build();

    when(dfsRefDatabase.updateRefId(
            refEvent.getRefName(), refEvent.command.getNewId(), refEvent.command.getOldId()))
        .thenReturn(false);

    expectedException.expect(ValidationException.class);
    expectedException.expectCause(nullValue(Exception.class));

    validator.onRefOperation(refEvent);
  }

  @Test
  public void shouldFailRefUpdateIfRefIsNotInDfsRefDatabase() throws ValidationException {
    final RefReceivedEvent refEvent = new RefReceivedEventBuilder().build();

    NoSuchElementException notInDb = new NoSuchElementException("obj is not in db");

    doThrow(notInDb)
        .when(dfsRefDatabase)
        .updateRefId(
            refEvent.getRefName(), refEvent.command.getNewId(), refEvent.command.getOldId());

    expectedException.expect(ValidationException.class);
    expectedException.expectCause(sameInstance(notInDb));

    validator.onRefOperation(refEvent);
  }

  @Test
  public void shouldDeleteRefInDfsDatabaseWhenHandlingRefDeleteEvents() throws ValidationException {
    final RefReceivedEvent refEvent = new RefReceivedEventBuilder().refDeletion().build();

    when(dfsRefDatabase.deleteRef(refEvent.getRefName(), refEvent.command.getOldId()))
        .thenReturn(true);

    final List<ValidationMessage> validationMessages = validator.onRefOperation(refEvent);

    assertThat(validationMessages).isEmpty();

    verify(dfsRefDatabase).deleteRef(refEvent.getRefName(), refEvent.command.getOldId());
  }

  @Test
  public void shouldFailRefDeletionIfRefDeletionInDfsRefDatabaseReturnsFalse()
      throws ValidationException {
    final RefReceivedEvent refEvent = new RefReceivedEventBuilder().refDeletion().build();

    when(dfsRefDatabase.deleteRef(refEvent.getRefName(), refEvent.command.getOldId()))
        .thenReturn(false);

    expectedException.expect(ValidationException.class);
    expectedException.expectCause(nullValue(Exception.class));

    validator.onRefOperation(refEvent);
  }

  @Test
  public void shouldFailRefDeletionIfRefIsNotInDfsDatabase() throws ValidationException {
    final RefReceivedEvent refEvent = new RefReceivedEventBuilder().refDeletion().build();

    NoSuchElementException notInDb = new NoSuchElementException("obj is not in db");

    doThrow(notInDb)
        .when(dfsRefDatabase)
        .deleteRef(refEvent.getRefName(), refEvent.command.getOldId());

    expectedException.expect(ValidationException.class);
    expectedException.expectCause(sameInstance(notInDb));

    validator.onRefOperation(refEvent);
  }

  static class RefReceivedEventBuilder {
    private ObjectId oldId = ObjectId.fromString("1049eb6eee7e1318f4e78e799bf33f1e54af9cbf");
    private ObjectId newId = ObjectId.fromString("2049eb6eee7e1318f4e78e799bf33f1e54af9cbf");
    private String refName = "refs/heads/master";
    private String projectName = "AProject";

    public RefReceivedEventBuilder() {}

    public RefReceivedEvent build() {
      RefReceivedEvent result = new RefReceivedEvent();
      result.command = new ReceiveCommand(oldId, newId, refName);
      result.project = new Project(new Project.NameKey(projectName));
      result.user = null;

      return result;
    }

    public RefReceivedEventBuilder oldId(String id) {
      this.oldId = ObjectId.fromString(id);
      return this;
    }

    public RefReceivedEventBuilder newId(String id) {
      this.newId = ObjectId.fromString(id);
      return this;
    }

    public RefReceivedEventBuilder refCreation() {
      return oldId(ObjectId.zeroId().name());
    }

    public RefReceivedEventBuilder refDeletion() {
      return newId(ObjectId.zeroId().name());
    }

    public RefReceivedEventBuilder refName(String name) {
      this.refName = name;
      return this;
    }

    public RefReceivedEventBuilder projectName(String name) {
      this.projectName = name;
      return this;
    }
  }
}
