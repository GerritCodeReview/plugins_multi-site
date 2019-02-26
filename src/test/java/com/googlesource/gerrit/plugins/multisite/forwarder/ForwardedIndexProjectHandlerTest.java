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

package com.googlesource.gerrit.plugins.multisite.forwarder;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.index.project.ProjectIndexer;
import com.google.gerrit.reviewdb.client.Project;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexingHandler.Operation;
import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class ForwardedIndexProjectHandlerTest {

  @Rule public ExpectedException exception = ExpectedException.none();
  @Mock private ProjectIndexer indexerMock;
  @Mock private Configuration configMock;
  @Mock private Configuration.Index indexMock;
  private ForwardedIndexProjectHandler handler;
  private String nameKey;

  @Before
  public void setUp() {
    when(configMock.index()).thenReturn(indexMock);
    when(indexMock.numStripedLocks()).thenReturn(10);
    handler = new ForwardedIndexProjectHandler(indexerMock, configMock);
    nameKey = "project/name";
  }

  @Test
  public void testSuccessfulIndexing() throws Exception {
    handler.index(nameKey, Operation.INDEX, Optional.empty());
    verify(indexerMock).index(new Project.NameKey(nameKey));
  }

  @Test
  public void deleteIsNotSupported() throws Exception {
    exception.expect(UnsupportedOperationException.class);
    exception.expectMessage("Delete from project index not supported");
    handler.index(nameKey, Operation.DELETE, Optional.empty());
  }

  @Test
  public void shouldSetAndUnsetForwardedContext() throws Exception {
    // this doAnswer is to allow to assert that context is set to forwarded
    // while cache eviction is called.
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  return null;
                })
        .when(indexerMock)
        .index(new Project.NameKey(nameKey));

    assertThat(Context.isForwardedEvent()).isFalse();
    handler.index(nameKey, Operation.INDEX, Optional.empty());
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(indexerMock).index(new Project.NameKey(nameKey));
  }

  @Test
  public void shouldSetAndUnsetForwardedContextEvenIfExceptionIsThrown() throws Exception {
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  throw new IOException("someMessage");
                })
        .when(indexerMock)
        .index(new Project.NameKey(nameKey));

    assertThat(Context.isForwardedEvent()).isFalse();
    try {
      handler.index(nameKey, Operation.INDEX, Optional.empty());
      fail("should have thrown an IOException");
    } catch (IOException e) {
      assertThat(e.getMessage()).isEqualTo("someMessage");
    }
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(indexerMock).index(new Project.NameKey(nameKey));
  }
}
