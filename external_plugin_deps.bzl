load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.3.1",
        sha1 = "5df9dddad2fc67c922406f41549186b210cd957e",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.5.0-alpha-202108301155",
        sha1 = "ef4d94bb4ba1d136cd90ea901776f03a25bcb517",
    )

