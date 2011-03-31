package org.jenkins.plugins.cloudbees;

import hudson.maven.*;
import hudson.model.Action;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TODO move this as only an Action and add a reall MavenAggregatedReport to store artifacts @update time
 * @author Olivier Lamy
 */
public class ArtifactFilePathSaveAction implements AggregatableAction,MavenAggregatedReport {

    final Set<MavenArtifactWithFilePath> mavenArtifactWithFilePaths;

    ArtifactFilePathSaveAction(Set<MavenArtifactWithFilePath> mavenArtifactWithFilePaths) {
        this.mavenArtifactWithFilePaths = mavenArtifactWithFilePaths;
    }

    public void update(Map<MavenModule, List<MavenBuild>> moduleBuilds, MavenBuild newBuild) {
        System.out.println("update");
    }

    public Class<? extends AggregatableAction> getIndividualActionType() {
        return null;
    }

    public Action getProjectAction(MavenModuleSet moduleSet) {
        return null;
    }

    public MavenAggregatedReport createAggregatedAction(MavenModuleSetBuild build, Map<MavenModule, List<MavenBuild>> moduleBuilds) {
        return this;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }

}
