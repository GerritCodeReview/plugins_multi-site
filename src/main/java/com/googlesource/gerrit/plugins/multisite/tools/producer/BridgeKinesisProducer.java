package com.googlesource.gerrit.plugins.multisite.tools.producer;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClient;
import com.googlesource.gerrit.plugins.multisite.tools.Config;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Base64;

public class BridgeKinesisProducer implements BridgeProducer {
    private static Config config;
    private static AmazonKinesis client;

    public BridgeKinesisProducer(Config c) {
        config = c;
        client = AmazonKinesisClient.builder().build();
    }

    @Override
    public void sendRecord(String destinationChannel, String payload) {
        ByteBuffer data = ByteBuffer.wrap(Base64.getEncoder().encodeToString(payload.getBytes()).getBytes(Charset.defaultCharset()));
        client.putRecord(destinationChannel, data, "partitionKey" + destinationChannel);
    }
}
