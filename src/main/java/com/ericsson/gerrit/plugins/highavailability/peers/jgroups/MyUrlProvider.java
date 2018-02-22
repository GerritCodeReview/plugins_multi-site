// Copyright (C) 2017 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.peers.jgroups;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MyUrlProvider implements Provider<String> {
  private static final Logger log = LoggerFactory.getLogger(MyUrlProvider.class);

  private static final String HTTPD_SECTION = "httpd";
  private static final String LISTEN_URL_KEY = "listenUrl";
  private static final String LISTEN_URL = HTTPD_SECTION + "." + LISTEN_URL_KEY;
  private static final String PROXY_PREFIX = "proxy-";

  private final String myUrl;

  @Inject
  @VisibleForTesting
  public MyUrlProvider(@GerritServerConfig Config srvConfig, Configuration pluginConfiguration) {
    String url = pluginConfiguration.peerInfoJGroups().myUrl();
    if (url == null) {
      log.info("myUrl not configured; attempting to determine from {}", LISTEN_URL);
      try {
        url = CharMatcher.is('/').trimTrailingFrom(getMyUrlFromListenUrl(srvConfig));
      } catch (MyUrlProviderException e) {
        throw new ProvisionException(e.getMessage());
      }
    }
    this.myUrl = url;
  }

  @Override
  public String get() {
    return myUrl;
  }

  private String getMyUrlFromListenUrl(Config srvConfig) throws MyUrlProviderException {
    String[] listenUrls = srvConfig.getStringList(HTTPD_SECTION, null, LISTEN_URL_KEY);
    if (listenUrls.length != 1) {
      throw new MyUrlProviderException(
          String.format(
              "Can only determine myUrl from %s when there is exactly 1 value configured; found %d",
              LISTEN_URL, listenUrls.length));
    }
    String url = listenUrls[0];
    if (url.startsWith(PROXY_PREFIX)) {
      throw new MyUrlProviderException(
          String.format(
              "Cannot determine myUrl from %s when configured as reverse-proxy: %s",
              LISTEN_URL, url));
    }
    if (url.contains("*")) {
      throw new MyUrlProviderException(
          String.format(
              "Cannot determine myUrl from %s when configured with wildcard: %s", LISTEN_URL, url));
    }
    try {
      URIish u = new URIish(url);
      return u.setHost(InetAddress.getLocalHost().getHostName()).toString();
    } catch (URISyntaxException | UnknownHostException e) {
      throw new MyUrlProviderException(
          String.format(
              "Unable to determine myUrl from %s value [%s]: %s", LISTEN_URL, url, e.getMessage()));
    }
  }

  private static class MyUrlProviderException extends Exception {
    private static final long serialVersionUID = 1L;

    MyUrlProviderException(String message) {
      super(message);
    }
  }
}
