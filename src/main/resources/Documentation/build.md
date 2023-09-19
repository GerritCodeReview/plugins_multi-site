# Build

This plugin can be built with Bazel in the Gerrit tree.

Clone or link this plugin to the plugins directory of Gerrit's
source tree.

Clone the [pull-replication](https://gerrit.googlesource.com/plugins/pull-replication) on
the same branch of the @PLUGIN@ plugin and link it to the `gerrit/plugins` directory.

```
  export BRANCH=$(git --git-dir=@PLUGIN@ branch)
  git clone https://gerrit.googlesource.com/plugins/pull-replication
  cd gerrit/plugins
  rm external_plugin_deps.bzl
  ln -s @PLUGIN@/external_plugin_deps.bzl .
```

Clone the [global-refdb](git clone "https://gerrit.googlesource.com/modules/global-refdb") on
the same branch of the @PLUGIN@ plugin and link it to the `gerrit/plugins` directory.

```
  export BRANCH=$(git --git-dir=@PLUGIN@ branch)
  git clone git clone "https://gerrit.googlesource.com/modules/global-refdb"
  cd gerrit/plugins
  rm external_plugin_deps.bzl
  ln -s @PLUGIN@/external_plugin_deps.bzl .
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
