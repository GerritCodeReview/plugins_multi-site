include_defs('//bucklets/gerrit_plugin.bucklet')
include_defs('//bucklets/java_sources.bucklet')
include_defs('//bucklets/maven_jar.bucklet')

SOURCES = glob(['src/main/java/**/*.java'])
RESOURCES = glob(['src/main/resources/**/*'])

DEPS = [
  ':wiremock',
]

TEST_DEPS = GERRIT_PLUGIN_API + GERRIT_TESTS + DEPS + [
  ':sync-index__plugin',
]

gerrit_plugin(
  name = 'sync-index',
  srcs = SOURCES,
  resources = RESOURCES,
  manifest_entries = [
    'Gerrit-PluginName: sync-index',
    'Gerrit-ApiType: plugin',
    'Gerrit-Module: com.ericsson.gerrit.plugins.syncindex.Module',
    'Gerrit-HttpModule: com.ericsson.gerrit.plugins.syncindex.HttpModule',
    'Implementation-Title: sync-index plugin',
    'Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/sync-index',
    'Implementation-Vendor: Ericsson',
  ],
  provided_deps = GERRIT_TESTS,
  deps = DEPS,
)

java_sources(
  name = 'sync-index-sources',
  srcs = SOURCES + RESOURCES,
)

java_library(
  name = 'classpath',
  deps = TEST_DEPS,
)

java_test(
  name = 'sync-index_tests',
  srcs = glob(['src/test/java/**/*.java']),
  labels = ['sync-index'],
  deps = TEST_DEPS,
)

maven_jar(
  name = 'wiremock',
  id = 'com.github.tomakehurst:wiremock:1.58:standalone',
  sha1 = '21c8386a95c5dc54a9c55839c5a95083e42412ae',
  license = 'Apache2.0',
  attach_source = False,
)
