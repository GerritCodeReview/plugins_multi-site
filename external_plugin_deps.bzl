load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.8.0-rc5",
        sha1 = "3f54f99e4ddf454c119e8ac3183bba7e9d7c7897",
    )
