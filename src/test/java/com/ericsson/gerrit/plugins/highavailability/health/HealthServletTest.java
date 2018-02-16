// Copyright (C) 2017 Ericsson
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

package com.ericsson.gerrit.plugins.highavailability.health;

import static com.google.gerrit.server.permissions.GlobalPermission.ADMINISTRATE_SERVER;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.WithUser;
import com.google.inject.Provider;
import java.io.IOException;
import java.nio.file.Files;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HealthServletTest {

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Mock private Provider<CurrentUser> currentUserProviderMock;
  @Mock private CurrentUser currentUserMock;
  @Mock private PermissionBackend permissionBackendMock;
  @Mock private WithUser withUserMock;

  private HealthServlet servlet;

  @Before
  public void setUp() throws Exception {
    when(currentUserProviderMock.get()).thenReturn(currentUserMock);
    when(permissionBackendMock.user(currentUserMock)).thenReturn(withUserMock);
    when(withUserMock.testOrFalse(ADMINISTRATE_SERVER)).thenReturn(true);
    servlet =
        new HealthServlet(
            currentUserProviderMock, permissionBackendMock, tempFolder.getRoot().toPath());
  }

  @Test
  public void shouldBeHealthyByDefault() {
    assertIsHealthy();
  }

  @Test
  public void testTransitionToUnhealthy() throws IOException {
    assertIsHealthy();

    // transition from healthy to unhealthy
    HttpServletResponse responseMock = mock(HttpServletResponse.class);
    servlet.doDelete(null, responseMock);
    verify(responseMock).setStatus(SC_NO_CONTENT);
    assertIsUnhealthy();

    // setting to unhealthy again should not change anything
    responseMock = mock(HttpServletResponse.class);
    servlet.doDelete(null, responseMock);
    verify(responseMock).setStatus(SC_NO_CONTENT);
    assertIsUnhealthy();
  }

  @Test
  public void testTransitionToUnhealthyByNonAdmins() throws IOException {
    assertIsHealthy();

    when(withUserMock.testOrFalse(ADMINISTRATE_SERVER)).thenReturn(false);
    HttpServletResponse responseMock = mock(HttpServletResponse.class);
    servlet.doDelete(null, responseMock);
    verify(responseMock).sendError(SC_FORBIDDEN);
    assertIsHealthy();
  }

  @Test
  public void testErrorDuringTransitionToUnhealthy() throws IOException {
    // remove plugin data dir to create an IOException
    tempFolder.delete();

    HttpServletResponse responseMock = mock(HttpServletResponse.class);
    servlet.doDelete(null, responseMock);
    verify(responseMock).sendError(SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  public void testTransitionToHealthy() throws IOException {
    // first, mark as unhealthy
    servlet.doDelete(null, mock(HttpServletResponse.class));
    assertIsUnhealthy();

    // transition from unhealthy to healthy
    HttpServletResponse responseMock = mock(HttpServletResponse.class);
    servlet.doPost(null, responseMock);
    verify(responseMock).setStatus(SC_NO_CONTENT);
    assertIsHealthy();

    // setting to healthy again should not change anything
    responseMock = mock(HttpServletResponse.class);
    servlet.doPost(null, responseMock);
    verify(responseMock).setStatus(SC_NO_CONTENT);
    assertIsHealthy();
  }

  @Test
  public void testTransitionToHealthyByNonAdmins() throws IOException {
    // first, mark as unhealthy
    servlet.doDelete(null, mock(HttpServletResponse.class));
    assertIsUnhealthy();

    when(withUserMock.testOrFalse(ADMINISTRATE_SERVER)).thenReturn(false);
    HttpServletResponse responseMock = mock(HttpServletResponse.class);
    servlet.doPost(null, responseMock);
    verify(responseMock).sendError(SC_FORBIDDEN);
    assertIsUnhealthy();
  }

  @Test
  public void testErrorDuringTransitionToHealthy() throws IOException {
    // Create unhealthy.txt as a folder with content to create an IOException
    Files.createFile(tempFolder.newFolder("unhealthy.txt").toPath().resolve("child"));

    HttpServletResponse responseMock = mock(HttpServletResponse.class);
    servlet.doPost(null, responseMock);
    verify(responseMock).sendError(SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  public void testErrorWhileSendingUnhealthyResponse() throws IOException {
    HttpServletResponse responseMock = mock(HttpServletResponse.class);
    servlet.doDelete(null, responseMock);
    verify(responseMock).setStatus(SC_NO_CONTENT);

    responseMock = mock(HttpServletResponse.class);
    doThrow(new IOException("someError")).when(responseMock).sendError(SC_SERVICE_UNAVAILABLE);
    servlet.doGet(null, responseMock);
    verify(responseMock).setStatus(SC_INTERNAL_SERVER_ERROR);
  }

  private void assertIsHealthy() {
    HttpServletResponse responseMock = mock(HttpServletResponse.class);
    servlet.doGet(null, responseMock);
    verify(responseMock).setStatus(SC_NO_CONTENT);
  }

  private void assertIsUnhealthy() throws IOException {
    HttpServletResponse responseMock = mock(HttpServletResponse.class);
    servlet.doGet(null, responseMock);
    verify(responseMock).sendError(SC_SERVICE_UNAVAILABLE);
  }
}
