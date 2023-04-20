load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.6.3.3",
        sha1 = "22c224fa7b3d493a45b553b9a50c04fd6c653de5",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.6.3",
        sha1 = "2a78d4492810d5b4280c6a92e6b8bbdadaffe7d2",
    )

