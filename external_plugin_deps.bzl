load("//tools/bzl:maven_jar.bzl", "maven_jar","MAVEN_LOCAL")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.3.1",
# TODO: we don't know yet what will be the final SHA1
#        sha1 = "75809e48c1ac3936cf6077b6590dedcd08fc4dd4",
    )

    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.3.1",
        sha1 = "90775e671946b20e52be3a11277d1ed33973d66e",
    )
