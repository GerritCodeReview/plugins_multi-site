load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.4.8.6",
        sha1 = "bb6f47aac55a630afe46d104011fa08d8539384f",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.4.0.4",
        sha1 = "8d361d863382290e33828116e65698190118d0f1",
    )
