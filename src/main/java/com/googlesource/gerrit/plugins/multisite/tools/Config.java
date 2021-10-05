package com.googlesource.gerrit.plugins.multisite.tools;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class Config {
    private static Config instance = null;

    public String KAFKA_BOOTSTRAP_SERVERS;
    public String KAFKA_GROUP_ID;

    public String KINESIS_ENDPOINT_URL;
    public String KINESIS_REGION;

    private Config() {
        String configFileName = "/Users/fabioponciroli/Development/gerriforge/multi-site/src/main/java/com/googlesource/gerrit/plugins/multisite/tools/bridge.cfg.template";
        Properties prop = new Properties();
        try (FileInputStream fis = new FileInputStream(configFileName)) {
            prop.load(fis);
        } catch (FileNotFoundException ex) {
            System.out.println("File not found " + Arrays.toString(ex.getStackTrace()));
        } catch (IOException ex) {
            System.out.println("Couldn't open file " + Arrays.toString(ex.getStackTrace()));
        }

        KAFKA_BOOTSTRAP_SERVERS = prop.getProperty("kafka.bootstrapServers", null);
        KAFKA_GROUP_ID = prop.getProperty("kafka.groupId", "BridgeKafkaConsumer");

        KINESIS_ENDPOINT_URL = prop.getProperty("kinesis.endpointUrl", null);
        KINESIS_REGION = prop.getProperty("kinesis.region", null);
    }

    public static Config getInstance() {
        if(instance==null)
        {
            instance = new Config();
        }
        return instance;
    }


    public static final List <String> TOPICS =
            Arrays.asList(
                    "gerrit",
                    "gerrit_stream",
                    "gerrit_index",
                    "gerrit_batch_index",
                    "gerrit_list_project",
                    "gerrit_cache_eviction");
}
