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
        "Gerrit-HttpModule: com.googlesource.gerrit.plugins.multisite.http.HttpModule",
        "Implementation-Title: multi-site plugin",
        "Implementation-URL: https://review.gerrithub.io/admin/repos/GerritForge/plugins_multi-site",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        ":events-broker-neverlink",
        ":global-refdb-neverlink",
        ":pull-replication-neverlink",
        ":replication-neverlink",
    ],
)

java_library(
    name = "replication-neverlink",
    neverlink = 1,
    exports = ["//plugins/replication"],
)

java_library(
    name = "pull-replication-neverlink",
    neverlink = 1,
    exports = ["//plugins/pull-replication"],
)

java_library(
    name = "events-broker-neverlink",
    neverlink = 1,
    exports = ["//plugins/events-broker"],
)

java_library(
    name = "global-refdb-neverlink",
    neverlink = 1,
    exports = ["//plugins/global-refdb"],
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
        "//plugins/events-broker",
        "//plugins/global-refdb",
        "//plugins/pull-replication",
        "//plugins/replication",
    ],
)

filegroup(
    name = "e2e_multi_site_test_dir",
    srcs = [
        "e2e-tests",
    ],
)

filegroup(
    name = "e2e_multi_site_setup_local_env_dir",
    srcs = [
        "setup_local_env",
    ],
)

sh_test(
    name = "e2e_multi_site_tests",
    srcs = [
        "e2e-tests/test.sh",
    ],
    args = [
        "--multisite-lib-file $(location //plugins/multi-site)",
        "--healthcheck-interval 5s",
        "--healthcheck-timeout 10s",
        "--healthcheck-retries 30",
        "--location '$(location //plugins/multi-site:e2e_multi_site_test_dir)'",
        "--local-env '$(location //plugins/multi-site:e2e_multi_site_setup_local_env_dir)'",
    ],
    data = [
        "//plugins/multi-site",
        "//plugins/multi-site:e2e_multi_site_test_dir",
        "//plugins/multi-site:e2e_multi_site_setup_local_env_dir",
    ] + glob(["setup_local_env/**/*"]) + glob(["e2e-tests/**/*"]),
    tags = [
        "e2e-multi-site",
    ],
)
