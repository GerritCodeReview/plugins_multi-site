load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "mockito",
        artifact = "org.mockito:mockito-core:2.27.0",
        sha1 = "835fc3283b481f4758b8ef464cd560c649c08b00",
        deps = [
            "@byte-buddy//jar",
            "@byte-buddy-agent//jar",
            "@objenesis//jar",
        ],
    )

    BYTE_BUDDY_VER = "1.9.10"

    maven_jar(
        name = "byte-buddy",
        artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VER,
        sha1 = "211a2b4d3df1eeef2a6cacf78d74a1f725e7a840",
    )

    maven_jar(
        name = "byte-buddy-agent",
        artifact = "net.bytebuddy:byte-buddy-agent:" + BYTE_BUDDY_VER,
        sha1 = "9674aba5ee793e54b864952b001166848da0f26b",
    )

    maven_jar(
        name = "objenesis",
        artifact = "org.objenesis:objenesis:2.6",
        sha1 = "639033469776fd37c08358c6b92a4761feb2af4b",
    )

    maven_jar(
        name = "kafka-client",
        artifact = "org.apache.kafka:kafka-clients:2.1.0",
        sha1 = "34d9983705c953b97abb01e1cd04647f47272fe5",
    )

    maven_jar(
        name = "testcontainers-kafka",
        artifact = "org.testcontainers:kafka:1.11.3",
        sha1 = "932d1baa2541f218b1b44a0546ae83d530011468",
    )

    CURATOR_VER = "4.2.0"

    maven_jar(
        name = "curator-test",
        artifact = "org.apache.curator:curator-test:" + CURATOR_VER,
        sha1 = "98ac2dd69b8c07dcaab5e5473f93fdb9e320cd73",
    )

    maven_jar(
        name = "curator-framework",
        artifact = "org.apache.curator:curator-framework:" + CURATOR_VER,
        sha1 = "5b1cc87e17b8fe4219b057f6025662a693538861",
    )

    maven_jar(
        name = "curator-recipes",
        artifact = "org.apache.curator:curator-recipes:" + CURATOR_VER,
        sha1 = "7f775be5a7062c2477c51533b9d008f70411ba8e",
    )

    maven_jar(
        name = "curator-client",
        artifact = "org.apache.curator:curator-client:" + CURATOR_VER,
        sha1 = "d5d50930b8dd189f92c40258a6ba97675fea3e15",
    )

    maven_jar(
        name = "zookeeper",
        artifact = "org.apache.zookeeper:zookeeper:3.4.14",
        sha1 = "c114c1e1c8172a7cd3f6ae39209a635f7a06c1a1",
    )
