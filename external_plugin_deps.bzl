load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "wiremock",
        artifact = "com.github.tomakehurst:wiremock-standalone:2.18.0",
        sha1 = "cf7776dc7a0176d4f4a990155d819279078859f9",
    )

    CURATOR_VER = "4.2.0"
    CURATOR_TEST_VER = "2.12.0"

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

    maven_jar(
        name = "curator-test",
        artifact = "org.apache.curator:curator-test:" + CURATOR_TEST_VER,
        sha1 = "0a797be57ba95b67688a7615f7ad41ee6b3ceff0"
    )

    maven_jar(
        name = "curator-framework",
        artifact = "org.apache.curator:curator-framework:" + CURATOR_VER,
        sha1 = "5b1cc87e17b8fe4219b057f6025662a693538861"
    )

    maven_jar(
        name = "curator-recipes",
        artifact = "org.apache.curator:curator-recipes:" + CURATOR_VER,
        sha1 = "7f775be5a7062c2477c51533b9d008f70411ba8e"
    )

    maven_jar(
        name = "curator-client",
        artifact = "org.apache.curator:curator-client:" + CURATOR_VER,
        sha1 = "d5d50930b8dd189f92c40258a6ba97675fea3e15"
        )

    maven_jar(
        name = "zookeeper",
        artifact = "org.apache.zookeeper:zookeeper:3.4.8",
        sha1 = "933ea2ed15e6a0e24b788973e3d128ff163c3136"
    )
