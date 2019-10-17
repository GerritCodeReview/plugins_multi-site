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
        "Gerrit-Module: com.googlesource.gerrit.plugins.multisite.PluginModule",
        "Implementation-Title: multi-site plugin",
        "Implementation-URL: https://review.gerrithub.io/admin/repos/GerritForge/plugins_multi-site",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        ":replication-neverlink",
        "//modules/events-broker",
        "//modules/global-refdb",
    ],
)

java_library(
    name = "replication-neverlink",
    neverlink = 1,
    exports = ["//plugins/replication"],
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
        "//modules/events-broker",
        "//modules/global-refdb",
        "//plugins/replication",
    ],
)
