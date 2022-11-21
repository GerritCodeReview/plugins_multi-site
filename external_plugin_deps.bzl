load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.4.4.3",
        sha1 = "104bfec7a1f8f7dece8343606eca6a307d561b9e",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.4.0.4",
        sha1 = "8d361d863382290e33828116e65698190118d0f1",
    )
