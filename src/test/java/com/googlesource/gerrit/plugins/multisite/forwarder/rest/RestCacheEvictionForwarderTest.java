package com.googlesource.gerrit.plugins.multisite.forwarder.rest;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gson.GsonBuilder;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.cache.Constants;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheEvictionForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.CacheEvictionEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.rest.HttpResponseHandler.HttpResult;
import com.googlesource.gerrit.plugins.multisite.peers.PeerInfo;
import java.io.IOException;
import java.util.Set;
import javax.net.ssl.SSLException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;

public class RestCacheEvictionForwarderTest {
  private static final String URL = "http://fake.com";
  private static final String PLUGIN_NAME = "multi-site";
  private static final String EMPTY_MSG = "";
  private static final String ERROR = "Error";
  private static final String PLUGINS = "plugins";
  private static final String SUCCESS = "Success";
  private static final boolean SUCCESSFUL = true;
  private static final boolean FAILED = false;

  // Index
  private static final String PROJECT_NAME = "test/project";

  // Event
  private CacheEvictionForwarder forwarder;
  private HttpSession httpSessionMock;

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() {
    httpSessionMock = mock(HttpSession.class);
    Configuration configMock = mock(Configuration.class, Answers.RETURNS_DEEP_STUBS);
    when(configMock.http().maxTries()).thenReturn(3);
    when(configMock.http().retryInterval()).thenReturn(10);
    Provider<Set<PeerInfo>> peersMock = mock(Provider.class);
    when(peersMock.get()).thenReturn(ImmutableSet.of(new PeerInfo(URL)));
    forwarder =
        new RestCacheEvictionForwarder(
            httpSessionMock, PLUGIN_NAME, configMock, peersMock); // TODO: Create provider
  }

  @Test
  public void testEvictProjectOK() throws Exception {
    String key = PROJECT_NAME;
    String keyJson = new GsonBuilder().create().toJson(key);
    when(httpSessionMock.post(buildCacheEndpoint(Constants.PROJECTS), keyJson))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.evict(new CacheEvictionEvent(Constants.PROJECTS, key))).isTrue();
  }

  @Test
  public void testEvictAccountsOK() throws Exception {
    Account.Id key = new Account.Id(123);
    String keyJson = new GsonBuilder().create().toJson(key);
    when(httpSessionMock.post(buildCacheEndpoint(Constants.ACCOUNTS), keyJson))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.evict(new CacheEvictionEvent(Constants.ACCOUNTS, key))).isTrue();
  }

  @Test
  public void testEvictGroupsOK() throws Exception {
    AccountGroup.Id key = new AccountGroup.Id(123);
    String keyJson = new GsonBuilder().create().toJson(key);
    String endpoint = buildCacheEndpoint(Constants.GROUPS);
    when(httpSessionMock.post(endpoint, keyJson)).thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.evict(new CacheEvictionEvent(Constants.GROUPS, key))).isTrue();
  }

  @Test
  public void testEvictGroupsByIncludeOK() throws Exception {
    AccountGroup.UUID key = new AccountGroup.UUID("90b3042d9094a37985f3f9281391dbbe9a5addad");
    String keyJson = new GsonBuilder().create().toJson(key);
    when(httpSessionMock.post(buildCacheEndpoint(Constants.GROUPS_BYINCLUDE), keyJson))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.evict(new CacheEvictionEvent(Constants.GROUPS_BYINCLUDE, key))).isTrue();
  }

  @Test
  public void testEvictGroupsMembersOK() throws Exception {
    AccountGroup.UUID key = new AccountGroup.UUID("90b3042d9094a37985f3f9281391dbbe9a5addad");
    String keyJson = new GsonBuilder().create().toJson(key);
    when(httpSessionMock.post(buildCacheEndpoint(Constants.GROUPS_MEMBERS), keyJson))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.evict(new CacheEvictionEvent(Constants.GROUPS_MEMBERS, key))).isTrue();
  }

  @Test
  public void testEvictCacheFailed() throws Exception {
    String key = PROJECT_NAME;
    String keyJson = new GsonBuilder().create().toJson(key);
    when(httpSessionMock.post(buildCacheEndpoint(Constants.PROJECTS), keyJson))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(forwarder.evict(new CacheEvictionEvent(Constants.PROJECTS, key))).isFalse();
  }

  @Test
  public void testEvictCacheThrowsException() throws Exception {
    String key = PROJECT_NAME;
    String keyJson = new GsonBuilder().create().toJson(key);
    doThrow(new IOException())
        .when(httpSessionMock)
        .post(buildCacheEndpoint(Constants.PROJECTS), keyJson);
    assertThat(forwarder.evict(new CacheEvictionEvent(Constants.PROJECTS, key))).isFalse();
  }

  @Test
  public void testRetryOnErrorThenSuccess() throws IOException {
    when(httpSessionMock.post(anyString(), anyString()))
        .thenReturn(new HttpResult(false, ERROR))
        .thenReturn(new HttpResult(false, ERROR))
        .thenReturn(new HttpResult(true, SUCCESS));

    assertThat(forwarder.evict(new CacheEvictionEvent(Constants.PROJECT_LIST, new Object())))
        .isTrue();
  }

  @Test
  public void testRetryOnIoExceptionThenSuccess() throws IOException {
    when(httpSessionMock.post(anyString(), anyString()))
        .thenThrow(new IOException())
        .thenThrow(new IOException())
        .thenReturn(new HttpResult(true, SUCCESS));

    assertThat(forwarder.evict(new CacheEvictionEvent(Constants.PROJECT_LIST, new Object())))
        .isTrue();
  }

  @Test
  public void testNoRetryAfterNonRecoverableException() throws IOException {
    when(httpSessionMock.post(anyString(), anyString()))
        .thenThrow(new SSLException("Non Recoverable"))
        .thenReturn(new HttpResult(true, SUCCESS));

    assertThat(forwarder.evict(new CacheEvictionEvent(Constants.PROJECT_LIST, new Object())))
        .isFalse();
  }

  @Test
  public void testFailureAfterMaxTries() throws IOException {
    when(httpSessionMock.post(anyString(), anyString()))
        .thenReturn(new HttpResult(false, ERROR))
        .thenReturn(new HttpResult(false, ERROR))
        .thenReturn(new HttpResult(false, ERROR));

    assertThat(forwarder.evict(new CacheEvictionEvent(Constants.PROJECT_LIST, new Object())))
        .isFalse();
  }

  private static String buildCacheEndpoint(String name) {
    return Joiner.on("/").join(URL, PLUGINS, PLUGIN_NAME, "cache", name);
  }
}
