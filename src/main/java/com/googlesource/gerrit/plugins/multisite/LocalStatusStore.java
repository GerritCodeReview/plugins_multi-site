package com.googlesource.gerrit.plugins.multisite;

import com.google.inject.Singleton;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Singleton
public class LocalStatusStore {

    private Map <String, ProjectLocalStatus> statusPerProject;

    public LocalStatusStore() {
        this.statusPerProject = new HashMap <String, ProjectLocalStatus>();
    }

    public Optional <Long> getLastReplicationTime(String projectName) {
        Optional<ProjectLocalStatus> maybeProjectLocalStatus =
                Optional.ofNullable(this.statusPerProject.get(projectName));
        return maybeProjectLocalStatus.map(ProjectLocalStatus::getLastRefUpdatedTimestamp);
    }

    public void updateRefUpdatedTimeFor(String projectName) {
        ProjectLocalStatus projectLocalStatus = new ProjectLocalStatus(Instant.now().toEpochMilli());
        this.statusPerProject.put(projectName, projectLocalStatus);
    }
}
