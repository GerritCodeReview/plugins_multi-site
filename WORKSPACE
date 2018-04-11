workspace(name = "high_availability")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "1419057b24e41cda9a6ee9d9c431b9005faf8190",
    #local_path = "/home/ehugare/workspaces/bazlets",
)

#Snapshot Plugin API
#load(
#    "@com_googlesource_gerrit_bazlets//:gerrit_api_maven_local.bzl",
#    "gerrit_api_maven_local",
#)

# Load snapshot Plugin API
#gerrit_api_maven_local()

# Release Plugin API
load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# Load release Plugin API
gerrit_api()

load("//:external_plugin_deps.bzl", "external_plugin_deps")

external_plugin_deps()
