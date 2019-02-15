load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "wiremock",
        artifact = "com.github.tomakehurst:wiremock-standalone:2.18.0",
        sha1 = "cf7776dc7a0176d4f4a990155d819279078859f9",
    )

    maven_jar(
        name = "mockito",
        artifact = "org.mockito:mockito-core:2.21.0",
        sha1 = "cdd1d0d5b2edbd2a7040735ccf88318c031f458b",
        deps = [
            "@byte_buddy//jar",
            "@byte_buddy_agent//jar",
            "@objenesis//jar",
        ],
    )

    BYTE_BUDDY_VER = "1.8.15"

    maven_jar(
        name = "byte_buddy",
        artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VER,
        sha1 = "cb36fe3c70ead5fcd016856a7efff908402d86b8",
    )

    maven_jar(
        name = "byte_buddy_agent",
        artifact = "net.bytebuddy:byte-buddy-agent:" + BYTE_BUDDY_VER,
        sha1 = "a2dbe3457401f65ad4022617fbb3fc0e5f427c7d",
    )

    maven_jar(
        name = "objenesis",
        artifact = "org.objenesis:objenesis:2.6",
        sha1 = "639033469776fd37c08358c6b92a4761feb2af4b",
    )

    maven_jar(
        name = "kafka_client",
        artifact = "org.apache.kafka:kafka-clients:2.1.0",
        sha1 = "34d9983705c953b97abb01e1cd04647f47272fe5",
    )

    maven_jar(
        name = "testcontainers-kafka",
        artifact = "org.testcontainers:kafka:1.10.6",
        sha1 = "5984e31306bd6c84a36092cdd19e0ef7e2268d98",
    )

    maven_jar(
        name = "commons-lang3",
        artifact = "org.apache.commons:commons-lang3:3.6",
        sha1 = "9d28a6b23650e8a7e9063c04588ace6cf7012c17",
    )