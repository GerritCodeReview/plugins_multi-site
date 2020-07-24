load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.1.2",
        sha1 = "6ddee3de0f3fe9254453118ae1eca481ec03e957",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.2.0-rc4",
        sha1 = "53e3f862ac2c2196dba716756ac9586f4b63af47",
    )
