load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "wiremock",
        artifact = "com.github.tomakehurst:wiremock-standalone:2.23.2",
        sha1 = "4a920d6c04fd2444c7bc94880adc8313f5b31ba3",
    )

    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.1.0-rc1",
        sha1 = "61fc8defaed9c364e6bfa101563e434fcc70038f",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.1.2",
        sha1 = "b4ed20d7be8a7023111c511ca5dc00ec18e9313a",
    )
