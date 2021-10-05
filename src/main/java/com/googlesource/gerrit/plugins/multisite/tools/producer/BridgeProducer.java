package com.googlesource.gerrit.plugins.multisite.tools.producer;

public interface BridgeProducer {

    void sendRecord(String destinationChannel, String payload);
}
