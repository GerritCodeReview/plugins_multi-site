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
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfo;
import com.google.common.base.Optional;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.URIish;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider which uses JGroups to find the peer gerrit instances. On startup every gerrit instance
 * joins a jgroups channel. Whenever the set of channel members changes each gerrit server publishes
 * its url to all channel members.
 *
 * <p>This provider maintains a list of all members which joined the jgroups channel. This may be
 * more than two. But will always pick the first node which sent its url as the peer to be returned
 * by {@link #get()}. It will continue to return that node until that node leaves the jgroups
 * channel.
 */
@Singleton
public class JGroupsPeerInfoProvider extends ReceiverAdapter
    implements Provider<Optional<PeerInfo>>, LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(JGroupsPeerInfoProvider.class);

  private final String myUrl;
  private final Configuration.PeerInfoJGroups jgroupsConfig;
  private final InetAddressFinder finder;

  private JChannel channel;
  private Optional<PeerInfo> peerInfo = Optional.absent();
  private Address peerAddress;

  @Inject
  JGroupsPeerInfoProvider(
      @GerritServerConfig Config srvConfig,
      Configuration pluginConfiguration,
      InetAddressFinder finder)
      throws UnknownHostException, URISyntaxException {
    String hostName = InetAddress.getLocalHost().getHostName();
    URIish u = new URIish(srvConfig.getString("httpd", null, "listenUrl"));
    this.myUrl = u.setHost(hostName).toString();
    this.jgroupsConfig = pluginConfiguration.peerInfoJGroups();
    this.finder = finder;
  }

  @Override
  public void receive(Message msg) {
    synchronized (this) {
      if (peerAddress != null) {
        return;
      }
      peerAddress = msg.getSrc();
      String url = (String) msg.getObject();
      peerInfo = Optional.of(new PeerInfo(url));
      log.info("receive(): Set new peerInfo: {}", url);
    }
  }

  @Override
  public void viewAccepted(View view) {
    log.info("viewAccepted(view: {}) called", view);

    synchronized (this) {
      if (view.getMembers().size() > 2) {
        log.warn(
            "{} members joined the jgroups channel {}. Only two members are supported. Members: {}",
            view.getMembers().size(),
            channel.getName(),
            view.getMembers());
      }
      if (peerAddress != null && !view.getMembers().contains(peerAddress)) {
        log.info("viewAccepted(): removed peerInfo");
        peerAddress = null;
        peerInfo = Optional.absent();
      }
    }
    if (view.size() > 1) {
      try {
        channel.send(new Message(null, myUrl));
      } catch (Exception e) {
        // channel communication caused an error. Can't do much about it.
        log.error(
            "Sending a message over jgroups channel {} failed", jgroupsConfig.clusterName(), e);
      }
    }
  }

  public void connect() {
    try {
      channel = new JChannel();
      Optional<InetAddress> address = finder.findAddress();
      if (address.isPresent()) {
        channel.getProtocolStack().getTransport().setBindAddress(address.get());
      }
      channel.setReceiver(this);
      channel.setDiscardOwnMessages(true);
      channel.connect(jgroupsConfig.clusterName());
      log.info("Succesfully joined jgroups channel {}", channel);
    } catch (Exception e) {
      log.error("joining jgroups channel {} failed", e);
    }
  }

  @Override
  public Optional<PeerInfo> get() {
    return peerInfo;
  }

  @Override
  public void start() {
    connect();
  }

  @Override
  public void stop() {
    log.info("closing jgroups channel {}", jgroupsConfig.clusterName());
    channel.close();
    peerInfo = Optional.absent();
    peerAddress = null;
  }
}
