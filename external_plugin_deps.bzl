load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.4.8.6",
        sha1 = "5b8e943f94c64e3164e0d78f1c27795db7f72a4f",
    )
