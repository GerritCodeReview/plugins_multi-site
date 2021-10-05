package com.googlesource.gerrit.plugins.multisite.tools.producer;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClient;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.googlesource.gerrit.plugins.multisite.tools.Config;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Objects;

public class BridgeKinesisProducer implements BridgeProducer {
    private static Config config;
    private static AmazonKinesis client;

    public BridgeKinesisProducer(Config c) {
        config = c;

        System.out.println(c.KINESIS_REGION);
        AmazonKinesisClientBuilder builder = AmazonKinesisClient.builder().withRegion(c.KINESIS_REGION);
        if (Objects.nonNull(c.KINESIS_ENDPOINT_URL)) {
            builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:4566", c.KINESIS_REGION));
        }
        client = builder.build();
    }

    @Override
    public void sendRecord(String destinationChannel, String payload) {
        ByteBuffer data = ByteBuffer.wrap(Base64.getEncoder().encodeToString(payload.getBytes()).getBytes(Charset.defaultCharset()));
        System.out.printf("Sending record to AWS Kinesis '%s' stream", destinationChannel);
        client.putRecord(destinationChannel, data, "partitionKey" + destinationChannel);
    }
}
