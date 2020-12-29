# Build

This plugin can be built with Bazel in the Gerrit tree.

Clone or link this plugin to the plugins directory of Gerrit's
source tree. Put the external dependency Bazel build file into
the Gerrit /plugins directory, replacing the existing empty one.

```
  cd gerrit/plugins
  rm external_plugin_deps.bzl
  ln -s multi-site/external_plugin_deps.bzl .
```

From the Gerrit source tree issue the command:

```
  bazel build plugins/multi-site
```

The output is created in

```
  bazel-bin/plugins/multi-site/multi-site.jar
```

To execute the tests run:

```
  bazel test --test_tag_filters=multi-site
```

[Back to multi-site documentation index][index]

[index]: index.html
