load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.8.0-rc5",
        sha1 = "3f54f99e4ddf454c119e8ac3183bba7e9d7c7897",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.8.0-rc5",
        sha1 = "d62a2e5e49a6f77fff1221d05835bc84481bcb0a",
    )

