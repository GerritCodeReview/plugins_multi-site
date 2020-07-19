load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.1.2",
        sha1 = "6ddee3de0f3fe9254453118ae1eca481ec03e957",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.1.4",
        sha1 = "5672908dde0bd02cabc95efe34a8d8507d44b6ac",
    )
