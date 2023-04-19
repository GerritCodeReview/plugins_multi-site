load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.5.4.2",
        sha1 = "6fc0b656e2b955e51fc51264519c6a998b08f676",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.5.0.1",
        sha1 = "af192a8bceaf7ff54d19356f9bfe1f1e83634b40",
    )

