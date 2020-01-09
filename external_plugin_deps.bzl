load("//tools/bzl:maven_jar.bzl", "maven_jar")

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
        artifact = "com.gerritforge:events-broker:3.0.5",
        sha1 = "7abf72d2252f975baff666fbbf28b7036767aa81",
    )
