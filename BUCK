include_defs('//bucklets/gerrit_plugin.bucklet')
include_defs('//bucklets/java_sources.bucklet')
include_defs('//bucklets/maven_jar.bucklet')

SOURCES = glob(['src/main/java/**/*.java'])
RESOURCES = glob(['src/main/resources/**/*'])

TEST_DEPS = GERRIT_PLUGIN_API + GERRIT_TESTS + [
  ':high-availability__plugin',
  ':mockito',
  ':wiremock',
# bazlets include those 3 bouncycastle jars in plugin API so this is temporary
# until this plugin is built with bazel.
# see https://gerrit-review.googlesource.com/#/c/102670/ for more info.
  ':bouncycastle_bcprov',
  ':bouncycastle_bcpg',
  ':bouncycastle_bcpkix',
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
  resources = glob(['src/test/resources/**/']),
  labels = ['high-availability'],
  deps = TEST_DEPS,
)

maven_jar(
  name = 'wiremock',
  id = 'com.github.tomakehurst:wiremock-standalone:2.5.1',
  sha1 = '9cda1bf1674c8de3a1116bae4d7ce0046a857d30',
  license = 'Apache2.0',
  attach_source = False,
)

maven_jar(
  name = 'mockito',
  id = 'org.mockito:mockito-core:2.7.21',
  sha1 = '23e9f7bfb9717e849a05b84c29ee3ac723f1a653',
  license = 'DO_NOT_DISTRIBUTE',
  deps = [
    ':byte-buddy',
    ':objenesis',
  ],
)

maven_jar(
  name = 'byte-buddy',
  id = 'net.bytebuddy:byte-buddy:1.6.11',
  sha1 = '8a8f9409e27f1d62c909c7eef2aa7b3a580b4901',
  license = 'DO_NOT_DISTRIBUTE',
  attach_source = False,
)

maven_jar(
  name = 'objenesis',
  id = 'org.objenesis:objenesis:2.5',
  sha1 = '612ecb799912ccf77cba9b3ed8c813da086076e9',
  license = 'DO_NOT_DISTRIBUTE',
  attach_source = False,
)

BC_VERS = '1.56'

maven_jar(
  name = 'bouncycastle_bcprov',
  id = 'org.bouncycastle:bcprov-jdk15on:' + BC_VERS,
  sha1 = 'a153c6f9744a3e9dd6feab5e210e1c9861362ec7',
)

maven_jar(
  name = 'bouncycastle_bcpg',
  id = 'org.bouncycastle:bcpg-jdk15on:' + BC_VERS,
  sha1 = '9c3f2e7072c8cc1152079b5c25291a9f462631f1',
)

maven_jar(
  name = 'bouncycastle_bcpkix',
  id = 'org.bouncycastle:bcpkix-jdk15on:' + BC_VERS,
  sha1 = '4648af70268b6fdb24674fb1fd7c1fcc73db1231',
)
