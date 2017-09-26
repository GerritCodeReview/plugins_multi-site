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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

@Singleton
public class InetAddressFinder {

  private final boolean preferIPv4;
  private final Configuration.JGroups jgroupsConfig;

  @Inject
  InetAddressFinder(Configuration pluginConfiguration) {
    preferIPv4 = Boolean.getBoolean("java.net.preferIPv4Stack");
    jgroupsConfig = pluginConfiguration.jgroups();
  }

  /**
   * Iterate over all network interfaces and return the first appropriate address. Interfaces which
   * are loopback interfaces, or down or which don't support multicast are not inspected. Interfaces
   * whose name matches a {@code #skipInterface} are also ignored. By that it is possible to skip
   * interfaces which should not be used by jgroups (e.g. 'lo0', 'utun0' on MacOS).
   *
   * @return an Optional<InetAddress>
   */
  public Optional<InetAddress> findAddress() throws SocketException {
    return findFirstAppropriateAddress(Collections.list(NetworkInterface.getNetworkInterfaces()));
  }

  @VisibleForTesting
  Optional<InetAddress> findFirstAppropriateAddress(List<NetworkInterface> networkInterfaces)
      throws SocketException {
    for (NetworkInterface ni : networkInterfaces) {
      if (ni.isLoopback() || !ni.isUp() || !ni.supportsMulticast()) {
        continue;
      }
      if (shouldSkip(ni.getName())) {
        continue;
      }
      Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
      while (inetAddresses.hasMoreElements()) {
        InetAddress a = inetAddresses.nextElement();
        if (preferIPv4 && a instanceof Inet4Address) {
          return Optional.of(a);
        }
        if (!preferIPv4 && a instanceof Inet6Address) {
          return Optional.of(a);
        }
      }
    }
    return Optional.empty();
  }

  @VisibleForTesting
  boolean shouldSkip(String name) {
    for (String s : jgroupsConfig.skipInterface()) {
      if (s.endsWith("*") && name.startsWith(s.substring(0, s.length() - 1))) {
        return true;
      }
      if (name.equals(s)) {
        return true;
      }
    }
    return false;
  }
}
