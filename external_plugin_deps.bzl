load("//tools/bzl:maven_jar.bzl", "maven_jar","MAVEN_LOCAL")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.3.2",
        repository = MAVEN_LOCAL,
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.3.1",
        sha1 = "90775e671946b20e52be3a11277d1ed33973d66e",
    )
