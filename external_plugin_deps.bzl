load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.4.0",
        sha1 = "a1c7b02ddabe0dd0a989fb30ca18b61fe95ee894",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.4.0",
        sha1 = "031881f18def90f945b21c7aafda3a1ac95e89c8",
    )
