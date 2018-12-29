load("//tools/bzl:junit.bzl", "junit_tests")
load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)

gerrit_plugin(
    name = "high-availability",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: high-availability",
        "Gerrit-Module: com.ericsson.gerrit.plugins.highavailability.Module",
        "Gerrit-HttpModule: com.ericsson.gerrit.plugins.highavailability.HttpModule",
        "Implementation-Title: high-availability plugin",
        "Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/high-availability",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        "@jgroups//jar",
    ],
)

junit_tests(
    name = "high_availability_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    resources = glob(["src/test/resources/**/*"]),
    tags = [
        "high-availability",
        "local",
    ],
    deps = [
        ":high-availability__plugin_test_deps",
    ],
)

java_library(
    name = "high-availability__plugin_test_deps",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":high-availability__plugin",
        "@mockito//jar",
        "@wiremock//jar",
    ],
)
