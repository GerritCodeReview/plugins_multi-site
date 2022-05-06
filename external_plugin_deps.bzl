load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.6.0-rc3.1",
        sha1 = "9e28fdc65dcdecefd852f6c24dd7eec339a01870",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.6.0-rc3",
        sha1 = "cb398afa4f76367be5c62b99a7ffce74ae1d3d8b",
    )

