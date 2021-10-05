package com.googlesource.gerrit.plugins.multisite.tools;

import java.util.Arrays;
import java.util.List;

public class Config {

    Config() {}

    public static final List <String> TOPICS =
            Arrays.asList(
                    "gerrit_stream",
                    "gerrit_index",
                    "gerrit_batch_index",
                    "gerrit_list_project",
                    "gerrit_cache_eviction");
}
