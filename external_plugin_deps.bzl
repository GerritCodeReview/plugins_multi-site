load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.5.4.3",
        sha1 = "1c3f986f1c7b992fd7a7df45c81bf24849171224",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.5.0.1",
        sha1 = "af192a8bceaf7ff54d19356f9bfe1f1e83634b40",
    )

