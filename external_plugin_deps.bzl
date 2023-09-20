load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.7.2.1",
        sha1 = "be8177669a281f8d14e9e3b3231ee86a806710d3",
    )
