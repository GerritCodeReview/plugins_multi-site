load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.3.0-rc1",
        sha1 = "1b005b31c27a30ff10de97f903fa2834051bcadf",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.2.0-rc4",
        sha1 = "53e3f862ac2c2196dba716756ac9586f4b63af47",
    )
