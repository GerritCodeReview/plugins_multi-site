load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.6.3.4",
        sha1 = "1bd96a9f80b35e24a9b17becb0f49a510758a2fa",
    )
