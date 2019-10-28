load("//tools/bzl:maven_jar.bzl", "MAVEN_LOCAL", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "wiremock",
        artifact = "com.github.tomakehurst:wiremock-standalone:2.23.2",
        sha1 = "4a920d6c04fd2444c7bc94880adc8313f5b31ba3",
    )

    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:0.1.1",
        sha1 = "d6ab59906db7b20a52c8994502780b2a6ab23872",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.0.1-SNAPSHOT",
        sha1 = "f733ae91325db994ffd546b28f8aa6aedb578806",
        repository = MAVEN_LOCAL,
    )
