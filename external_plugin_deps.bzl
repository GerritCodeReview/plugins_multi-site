load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.4.8.2",
        sha1 = "e389f948e2da0a34893d78cb4030a6ee157d66b7",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.4.0.4",
        sha1 = "8d361d863382290e33828116e65698190118d0f1",
    )
