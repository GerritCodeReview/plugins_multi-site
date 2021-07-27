load("//tools/bzl:maven_jar.bzl", "MAVEN_LOCAL", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "global-refdb",
        artifact = "com.gerritforge:global-refdb:3.3.1",
        sha1 = "5df9dddad2fc67c922406f41549186b210cd957e",
    )

    # TODO: Point to maven once we have events-broker-3.5.0.jar,
    # that includes the StreamEventPublisher
    maven_jar(
        name = "events-broker",
        artifact = "com.gerritforge:events-broker:3.5.0",
        repository = MAVEN_LOCAL,
        sha1 = "c9bfe54bdb4fd397630edd5d00bca4da7ab9d779",
    )
