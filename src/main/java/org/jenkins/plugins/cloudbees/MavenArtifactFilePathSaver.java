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

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.maven.*;
import hudson.maven.reporters.MavenArtifact;
import hudson.maven.reporters.MavenArtifactArchiver;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.maven.reporters.Messages;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.util.InvocationInterceptor;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Olivier Lamy
 */
public class MavenArtifactFilePathSaver extends MavenReporter {
    @Override
    public boolean preBuild(MavenBuildProxy build, MavenProject pom, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    @Override
    public boolean preExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    public boolean postBuild(MavenBuildProxy build, MavenProject pom, final BuildListener listener) throws InterruptedException, IOException {

        if(pom.getFile()!=null) {
            final Set<MavenArtifactWithFilePath> mavenArtifacts = new HashSet<MavenArtifactWithFilePath>();
            // record main artifact (if packaging is POM, this doesn't exist)
            final MavenArtifact mainArtifact = MavenArtifact.create(pom.getArtifact());
            if(mainArtifact!=null) {
                //TODO take of NPE !!
                mavenArtifacts.add(new MavenArtifactWithFilePath(pom.getGroupId(), pom.getArtifactId(), pom.getVersion(), pom.getArtifact().getFile().getPath(),pom.getArtifact().getType()));
            }

            // record attached artifacts
            final List<MavenArtifact> attachedArtifacts = new ArrayList<MavenArtifact>();
            for( Artifact a : (List<Artifact>)pom.getAttachedArtifacts() ) {
                MavenArtifact ma = MavenArtifact.create(a);
                if(ma!=null) {
                //TODO take of NPE !!
                mavenArtifacts.add(new MavenArtifactWithFilePath(pom.getGroupId(),pom.getArtifactId(),pom.getVersion(),pom.getArtifact().getFile().getPath(),pom.getArtifact().getType()));
                }
            }

            // record the action
            build.execute(new MavenBuildProxy.BuildCallable<Void,IOException>() {
                public Void call(MavenBuild build) throws IOException, InterruptedException {

                    ArtifactFilePathSaveAction artifactFilePathSaveAction = build.getAction(ArtifactFilePathSaveAction.class);
                    if (artifactFilePathSaveAction == null) {
                        artifactFilePathSaveAction = new ArtifactFilePathSaveAction(mavenArtifacts);
                    } else {
                        artifactFilePathSaveAction.mavenArtifactWithFilePaths.addAll(mavenArtifacts);
                    }
                    build.addAction(artifactFilePathSaveAction);

                    System.out.println("record artifacst" + artifactFilePathSaveAction.mavenArtifactWithFilePaths);
                    //build.save();
                    return null;
                }
            });
        }


        return true;
    }

    @Extension
    public static final class DescriptorImpl extends MavenReporterDescriptor {
        public String getDisplayName() {
            return MavenArtifactFilePathSaver.class.getName();
        }

        public MavenReporter newAutoInstance(MavenModule module) {
            return new MavenArtifactFilePathSaver();
        }
    }

    private static final long serialVersionUID = 1L;
}
