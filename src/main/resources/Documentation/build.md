# Build

This plugin can be built with Bazel or Buck and two build modes are supported:

* Standalone
* In Gerrit tree (Buck only)

Standalone build mode is recommended, as this mode doesn't require local Gerrit
tree to exist. Moreover, there are some limitations and additional manual steps
required when building in Gerrit tree mode (see corresponding sections).

## Build standalone

### Bazel

To build the plugin, issue the following command:

```
  bazel build @PLUGIN@
```

The output is created in

```
  bazel-genfiles/@PLUGIN@.jar
```

To package the plugin sources run:

```
  bazel build lib@PLUGIN@__plugin-src.jar
```

The output is created in:

```
  bazel-bin/lib@PLUGIN@__plugin-src.jar
```

To execute the tests run:

```
  bazel test high_availability_tests
```

This project can be imported into the Eclipse IDE:

```
  ./tools/eclipse.py
```

### Buck

Clone bucklets library:

```
  git clone https://gerrit.googlesource.com/bucklets

```

and link it to @PLUGIN@ directory:

```
  cd @PLUGIN@ && ln -s ../bucklets .
```

Add link to the .buckversion file:

```
  cd @PLUGIN@ && ln -s bucklets/buckversion .buckversion
```

Add link to the .watchmanconfig file:

```
  cd @PLUGIN@ && ln -s bucklets/watchmanconfig .watchmanconfig
```

To build the plugin, issue the following command:

```
  buck build plugin
```

The output is created in:

```
  buck-out/gen/@PLUGIN@.jar
```

This project can be imported into the Eclipse IDE:

```
  ./bucklets/tools/eclipse.py
```

To execute the tests run:

```
  buck test
```

To build plugin sources run:

```
  buck build src
```

The output is created in:

```
  buck-out/gen/@PLUGIN@-sources.jar
```

## Build in Gerrit tree

### Bazel

Clone or link this plugin to the plugins directory of Gerrit's
source tree. Put the external dependency Bazel build file into
the Gerrit /plugins directory, replacing the existing empty one.

```
  cd gerrit/plugins
  rm external_plugin_deps.bzl
  ln -s @PLUGIN@/external_plugin_deps.bzl .
```

From Gerrit source tree issue the command:

```
  bazel build plugins/@PLUGIN@
```

The output is created in

```
  bazel-genfiles/plugins/@PLUGIN@/@PLUGIN@.jar
```

This project can be imported into the Eclipse IDE:
Add the plugin name to the `CUSTOM_PLUGINS` and to the
`CUSTOM_PLUGINS_TEST_DEPS` set in Gerrit core in
`tools/bzl/plugins.bzl`, and execute:

```
  ./tools/eclipse/project.py
```

### Buck

Clone or link this plugin to the plugins directory of Gerrit's source
tree, and issue the command:

```
  buck build plugins/@PLUGIN@
```

The output is created in:

```
  buck-out/gen/plugins/@PLUGIN@/@PLUGIN@.jar
```

This project can be imported into the Eclipse IDE:

```
  ./tools/eclipse/project.py
```

* Note: wiremock and mockito jars should be added manually to classpath. In
Eclipse:
`Project -> Java Build Path -> Add External JARS`


To execute the tests run:

```
  buck test --include @PLUGIN@
```

How to build the Gerrit Plugin API is described in the [Gerrit
documentation](../../../Documentation/dev-buck.html#_extension_and_plugin_api_jar_files).

[Back to @PLUGIN@ documentation index][index]

[index]: index.html
