/*
 * The MIT License
 *
 * Copyright (c) 2011-2014, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.plugins.deployer;

import com.cloudbees.EndPoints;
import com.cloudbees.plugins.deployer.hosts.DeployHost;
import com.cloudbees.plugins.deployer.impl.run.RunHostImpl;
import com.cloudbees.plugins.deployer.impl.run.RunTargetImpl;
import com.cloudbees.plugins.deployer.impl.run.RunTargetImpl.Setting;
import com.cloudbees.plugins.deployer.sources.DeploySource;
import com.cloudbees.plugins.deployer.sources.MavenArtifactDeploySource;
import com.cloudbees.plugins.deployer.sources.WildcardPathDeploySource;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A simple facade for configuring a job for deployment programmatically.
 * <p/>
 * This allow plugin implementation to change while this interface can
 * remain stable.
 *
 * @author Ryan Campbell
 */
public class JobConfigBuilder {

    private String account = null;
    private String appId = null;
    private DeploySource deploySource = null;
    private String user = null;
    private String applicationEnvironment = null;
    private String deploymentDescription = null;
    private String apiEndPoint = EndPoints.runAPI();
    private List<Setting> applicationConfig = new ArrayList<Setting>();
    private boolean overwrite = false;

    public JobConfigBuilder(String account, String appId) {
        this.account = account;
        this.appId = appId;
    }

    public JobConfigBuilder filePattern(String pattern) {
        this.deploySource = new WildcardPathDeploySource(pattern);
        return this;
    }

    public JobConfigBuilder mavenArtifact(String groupId, String artifactId, String classifier, String type) {
        this.deploySource = new MavenArtifactDeploySource(groupId, artifactId, classifier, type);
        return this;
    }

    public JobConfigBuilder applicationEnvironment(String env) {
        this.applicationEnvironment = env;
        return this;
    }

    public JobConfigBuilder user(String user) {
        this.user = user;
        return this;
    }

    public JobConfigBuilder addSetting(String key, String value) {
        applicationConfig.add(new Setting(key, value));
        return this;
    }

    public JobConfigBuilder overwrite(boolean overwrite) {
        this.overwrite = overwrite;
        return this;
    }

    public JobConfigBuilder apiEndPoint(String apiEndPoint) {
        this.apiEndPoint = apiEndPoint;
        return this;
    }

    public JobConfigBuilder deploymentDescription(String deploymentDescription) {
        this.deploymentDescription = deploymentDescription;
        return this;
    }

    /**
     * Configure a project to deploy the war when the build completes.
     *
     * @param project the project to configure
     * @throws IOException if the project configuration could not be saved.
     */
    public void configure(AbstractProject<?, ?> project) throws IOException {
        final List<DeployHost<?, ?>> config = new ArrayList<DeployHost<?, ?>>();
        if (deploySource == null) {
            deploySource = project instanceof MavenModuleSet
                    ? new MavenArtifactDeploySource(null, null, null, "war")
                    : new WildcardPathDeploySource("**/*.war");
        }
        if (deploySource instanceof MavenArtifactDeploySource && !(project instanceof MavenModuleSet)) {
            deploySource = new WildcardPathDeploySource("**/*.war");
        }
        if (!(deploySource instanceof MavenArtifactDeploySource) && project instanceof MavenModuleSet) {
            deploySource = new MavenArtifactDeploySource(null, null, null, "war");
        }
        config.add(new RunHostImpl(user, account, Arrays.asList(
                new RunTargetImpl(apiEndPoint, appId, applicationEnvironment, deploymentDescription,
                        applicationConfig.toArray(new Setting[applicationConfig.size()]),
                        deploySource, false, null, null, null))));

        if (project instanceof MavenModuleSet) {
            if (project.getPublishersList().get(DeployPublisher.class) == null || overwrite) {
                project.getPublishersList().remove(DeployPublisher.class);
                project.getPublishersList().add(new DeployPublisher(config, false));
            }
        } else if (project instanceof FreeStyleProject) {
            final FreeStyleProject freeStyleProject = (FreeStyleProject) project;
            if (freeStyleProject.getBuildersList().get(DeployBuilder.class) == null || overwrite) {
                freeStyleProject.getBuildersList().remove(DeployBuilder.class);
                freeStyleProject.getBuildersList().add(new DeployBuilder(config));
            }
        }
        if (project.getProperty(DeployNowJobProperty.class) == null && overwrite) {
            project.removeProperty(DeployNowJobProperty.class);
            project.addProperty(new DeployNowJobProperty(false, config));
        }
    }
}
