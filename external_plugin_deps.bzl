load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.4.8.6",
        sha1 = "5b8e943f94c64e3164e0d78f1c27795db7f72a4f",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.4.0.4",
        sha1 = "8d361d863382290e33828116e65698190118d0f1",
    )
