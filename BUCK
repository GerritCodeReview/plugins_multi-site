include_defs('//bucklets/gerrit_plugin.bucklet')
include_defs('//bucklets/java_sources.bucklet')
include_defs('//bucklets/maven_jar.bucklet')

SOURCES = glob(['src/main/java/**/*.java'])
RESOURCES = glob(['src/main/resources/**/*'])

DEPS = [
  ':wiremock',
]

PROVIDED_DEPS = GERRIT_TESTS + [
  '//lib:gson',
]

TEST_DEPS = GERRIT_PLUGIN_API + PROVIDED_DEPS + DEPS + [
  ':sync-events__plugin',
]

gerrit_plugin(
  name = 'sync-events',
  srcs = SOURCES,
  resources = RESOURCES,
  manifest_entries = [
    'Gerrit-PluginName: sync-events',
    'Gerrit-ApiType: plugin',
    'Gerrit-Module: com.ericsson.gerrit.plugins.syncevents.Module',
    'Gerrit-HttpModule: com.ericsson.gerrit.plugins.syncevents.HttpModule',
    'Implementation-Title: sync-events plugin',
    'Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/sync-events',
    'Implementation-Vendor: Ericsson',
  ],
  provided_deps = PROVIDED_DEPS,
  deps = DEPS,
)

java_sources(
  name = 'sync-events-sources',
  srcs = SOURCES + RESOURCES,
)

java_library(
  name = 'classpath',
  deps = TEST_DEPS,
)

java_test(
  name = 'sync-events_tests',
  srcs = glob(['src/test/java/**/*.java']),
  labels = ['sync-events'],
  source_under_test = [':sync-events__plugin'],
  deps = TEST_DEPS,
)

maven_jar(
  name = 'wiremock',
  id = 'com.github.tomakehurst:wiremock:1.58:standalone',
  sha1 = '21c8386a95c5dc54a9c55839c5a95083e42412ae',
  license = 'Apache2.0',
  attach_source = False,
)
