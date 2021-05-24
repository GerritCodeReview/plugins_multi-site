load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.3.1",
        sha1 = "5df9dddad2fc67c922406f41549186b210cd957e",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.4.0.1",
        sha1 = "2d406afa8787621442d855e4b458c97bd24f1198",
    )
