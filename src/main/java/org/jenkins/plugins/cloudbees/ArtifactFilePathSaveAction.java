/*
 * Copyright 2010-2011, CloudBees Inc., Olivier Lamy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkins.plugins.cloudbees;

import hudson.maven.AggregatableAction;
import hudson.maven.MavenAggregatedReport;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.Action;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TODO move this as only an Action and add a reall MavenAggregatedReport to store artifacts @update time
 *
 * @author Olivier Lamy
 */
public class ArtifactFilePathSaveAction
    implements AggregatableAction, MavenAggregatedReport
{

    final Set<MavenArtifactWithFilePath> mavenArtifactWithFilePaths;

    ArtifactFilePathSaveAction( Set<MavenArtifactWithFilePath> mavenArtifactWithFilePaths )
    {
        this.mavenArtifactWithFilePaths = mavenArtifactWithFilePaths;
    }

    public void update( Map<MavenModule, List<MavenBuild>> moduleBuilds, MavenBuild newBuild )
    {
        System.out.println( "update" );
    }

    public Class<? extends AggregatableAction> getIndividualActionType()
    {
        return null;
    }

    public Action getProjectAction( MavenModuleSet moduleSet )
    {
        return null;
    }

    public MavenAggregatedReport createAggregatedAction( MavenModuleSetBuild build,
                                                         Map<MavenModule, List<MavenBuild>> moduleBuilds )
    {
        return this;
    }

    public String getIconFileName()
    {
        return null;
    }

    public String getDisplayName()
    {
        return null;
    }

    public String getUrlName()
    {
        return null;
    }

}
