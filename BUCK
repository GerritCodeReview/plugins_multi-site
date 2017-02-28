include_defs('//bucklets/gerrit_plugin.bucklet')
include_defs('//bucklets/java_sources.bucklet')
include_defs('//bucklets/maven_jar.bucklet')

SOURCES = glob(['src/main/java/**/*.java'])
RESOURCES = glob(['src/main/resources/**/*'])

TEST_DEPS = GERRIT_PLUGIN_API + GERRIT_TESTS + [
  ':high-availability__plugin',
  ':mockito',
  ':wiremock',
]

gerrit_plugin(
  name = 'high-availability',
  srcs = SOURCES,
  resources = RESOURCES,
  manifest_entries = [
    'Gerrit-PluginName: high-availability',
    'Gerrit-ApiType: plugin',
    'Gerrit-Module: com.ericsson.gerrit.plugins.highavailability.Module',
    'Gerrit-HttpModule: com.ericsson.gerrit.plugins.highavailability.HttpModule',
    'Implementation-Title: high-availability plugin',
    'Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/high-availability',
    'Implementation-Vendor: Ericsson',
  ],
  provided_deps = GERRIT_TESTS,
)

java_sources(
  name = 'high-availability-sources',
  srcs = SOURCES + RESOURCES,
)

java_library(
  name = 'classpath',
  deps = TEST_DEPS,
)

java_test(
  name = 'high-availability_tests',
  srcs = glob(['src/test/java/**/*.java']),
  labels = ['high-availability'],
  deps = TEST_DEPS,
)

maven_jar(
  name = 'wiremock',
  id = 'com.github.tomakehurst:wiremock:1.58:standalone',
  sha1 = '21c8386a95c5dc54a9c55839c5a95083e42412ae',
  license = 'Apache2.0',
  attach_source = False,
)

maven_jar(
  name = 'mockito',
  id = 'org.mockito:mockito-core:2.5.0',
  sha1 = 'be28d46a52c7f2563580adeca350145e9ce916f8',
  license = 'DO_NOT_DISTRIBUTE',
  deps = [
    ':byte-buddy',
    ':objenesis',
  ],
)

maven_jar(
  name = 'byte-buddy',
  id = 'net.bytebuddy:byte-buddy:1.5.12',
  sha1 = 'b1ba1d15f102b36ed43b826488114678d6d413da',
  license = 'DO_NOT_DISTRIBUTE',
  attach_source = False,
)

maven_jar(
  name = 'objenesis',
  id = 'org.objenesis:objenesis:2.4',
  sha1 = '2916b6c96b50c5b3ec4452ed99401db745aabb27',
  license = 'DO_NOT_DISTRIBUTE',
  attach_source = False,
)
