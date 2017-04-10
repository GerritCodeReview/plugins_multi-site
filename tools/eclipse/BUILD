load("@com_googlesource_gerrit_bazlets//tools:commons.bzl",
     "PLUGIN_DEPS",
     "PLUGIN_TEST_DEPS",)
load("@com_googlesource_gerrit_bazlets//tools:classpath.bzl",
     "classpath_collector")

java_library(
  name = "classpath",
    runtime_deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
      "@wiremock//jar",
      "@mockito//jar",
      "@byte-buddy//jar",
      "@objenesis//jar",
      "//:high-availability__plugin",
    ],
)

classpath_collector(
  name = "main_classpath_collect",
  deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
    "@wiremock//jar",
    "@mockito//jar",
    "@byte-buddy//jar",
    "@objenesis//jar",
    "//:high-availability__plugin"
  ],
)
