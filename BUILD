load("//tools/bzl:junit.bzl", "junit_tests")
load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)

gerrit_plugin(
    name = "multi-site",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: multi-site",
        "Gerrit-Module: com.googlesource.gerrit.plugins.multisite.Module",
        "Implementation-Title: multi-site plugin",
        "Implementation-URL: https://review.gerrithub.io/admin/repos/GerritForge/plugins_multi-site",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        "@curator-client//jar",
        "@curator-framework//jar",
        "@curator-recipes//jar",
        "@kafka-client//jar",
        "@zookeeper//jar",
    ],
)

junit_tests(
    name = "multi_site_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    resources = glob(["src/test/resources/**/*"]),
    tags = [
        "local",
        "multi-site",
    ],
    deps = [
        ":multi-site__plugin_test_deps",
    ],
)

java_library(
    name = "multi-site__plugin_test_deps",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":multi-site__plugin",
        "@curator-client//jar",
        "@curator-framework//jar",
        "@curator-test//jar",
        "@kafka-client//jar",
        "@mockito//jar",
        "@testcontainers-kafka//jar",
        "@wiremock//jar",
        "//lib/testcontainers",
    ],
)
