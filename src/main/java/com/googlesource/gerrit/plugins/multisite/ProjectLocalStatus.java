package com.googlesource.gerrit.plugins.multisite;

public class ProjectLocalStatus {

    private Long lastRefUpdatedTimestamp;

    public ProjectLocalStatus(Long lastRefUpdatedTimestamp) {
        this.lastRefUpdatedTimestamp = lastRefUpdatedTimestamp;
    }

    public Long getLastRefUpdatedTimestamp() {
        return this.lastRefUpdatedTimestamp;
    }
}
