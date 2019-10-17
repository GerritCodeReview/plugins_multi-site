# Build

This plugin can be built with Bazel in the Gerrit tree.

Clone or link this plugin to the plugins directory of Gerrit's
source tree. Put the external dependency Bazel build file into
the Gerrit /plugins directory, replacing the existing empty one.

```
  cd gerrit/plugins
  rm external_plugin_deps.bzl
  ln -s @PLUGIN@/external_plugin_deps.bzl .
```

Clone or link global-refdb module into modules directory:

```
  cd gerrit/modules
  git clone https://review.gerrithub.io/GerritForge/global-refdb
```

Clone or link events-broker module into modules directory:

```
  cd gerrit/modules
  git clone https://gerrit.googlesource.com/modules/events-broker
```

From the Gerrit source tree issue the command:

```
  bazel build plugins/@PLUGIN@
```

The output is created in

```
  bazel-bin/plugins/@PLUGIN@/@PLUGIN@.jar
```

To execute the tests run:

```
  bazel test --test_tag_filters=@PLUGIN@
```

[Back to @PLUGIN@ documentation index][index]

[index]: index.html
