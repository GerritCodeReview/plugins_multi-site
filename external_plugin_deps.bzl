load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "wiremock",
        artifact = "com.github.tomakehurst:wiremock-standalone:2.23.2",
        sha1 = "4a920d6c04fd2444c7bc94880adc8313f5b31ba3",
    )

    maven_jar(
        name = "kafka-client",
        artifact = "org.apache.kafka:kafka-clients:2.1.0",
        sha1 = "34d9983705c953b97abb01e1cd04647f47272fe5",
    )

    maven_jar(
        name = "testcontainers-kafka",
        artifact = "org.testcontainers:kafka:1.11.3",
        sha1 = "932d1baa2541f218b1b44a0546ae83d530011468",
    )

    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:0.1.1",
        sha1 = "d6ab59906db7b20a52c8994502780b2a6ab23872",
    )
