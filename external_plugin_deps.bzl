load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.3.1",
        sha1 = "5df9dddad2fc67c922406f41549186b210cd957e",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.4.0-rc0",
        sha1 = "8c34c88103d4783eb4c4decde6d93541bc1cf064",
    )
