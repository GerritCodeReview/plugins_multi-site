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
  ':evict-cache__plugin',
]

gerrit_plugin(
  name = 'evict-cache',
  srcs = SOURCES,
  resources = RESOURCES,
  manifest_entries = [
    'Gerrit-PluginName: evict-cache',
    'Gerrit-ApiType: plugin',
    'Gerrit-Module: com.ericsson.gerrit.plugins.evictcache.Module',
    'Gerrit-HttpModule: com.ericsson.gerrit.plugins.evictcache.HttpModule',
    'Implementation-Title: evict-cache plugin',
    'Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/evict-cache',
    'Implementation-Vendor: Ericsson',
  ],
  provided_deps = PROVIDED_DEPS,
  deps = DEPS,
)

java_sources(
  name = 'evict-cache-sources',
  srcs = SOURCES + RESOURCES,
)

java_library(
  name = 'classpath',
  deps = TEST_DEPS,
)

java_test(
  name = 'evict-cache_tests',
  srcs = glob(['src/test/java/**/*.java']),
  labels = ['evict-cache'],
  source_under_test = [':evict-cache__plugin'],
  deps = TEST_DEPS,
)

maven_jar(
  name = 'wiremock',
  id = 'com.github.tomakehurst:wiremock:1.58:standalone',
  sha1 = '21c8386a95c5dc54a9c55839c5a95083e42412ae',
  license = 'Apache2.0',
  attach_source = False,
)
