load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.0.2",
        sha1 = "293a807bd82a284c215213b442b3930258e01f5e",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.0.5",
        sha1 = "7abf72d2252f975baff666fbbf28b7036767aa81",
    )
