load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.5.0.1",
        sha1 = "70ce61d4b486be23da935d48a5ce27b19108c8ae",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.5.0.1",
        sha1 = "af192a8bceaf7ff54d19356f9bfe1f1e83634b40",
    )

