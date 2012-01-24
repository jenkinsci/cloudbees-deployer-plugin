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

package com.cloudbees.plugins.deployer.sources;

import com.cloudbees.plugins.deployer.DeployNowProjectAction;
import com.cloudbees.plugins.deployer.DeployNowRunAction;
import com.cloudbees.plugins.deployer.resolvers.CapabilitiesResolver;
import com.cloudbees.plugins.deployer.targets.DeployTarget;
import com.cloudbees.plugins.deployer.targets.DeployTargetDescriptor;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.FilePath;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Run;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.apache.maven.project.MavenProject;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.List;

/**
 * The base class for all {@link DeploySource} {@link Descriptor} instances.
 *
 * @since 4.0
 */
public abstract class DeploySourceDescriptor extends Descriptor<DeploySource> {

    /**
     * Returns {@code true} if and only if the supplied type is a Maven job type class.
     *
     * @param jobType the job type.
     * @return {@code true} if and only if the supplied type is a Maven job type class.
     * @since 4.17
     */
    public static boolean isMavenJob(Class<? extends AbstractProject> jobType) {
        return jobType != null && (MavenModuleSet.class.isAssignableFrom(jobType) || MavenProject.class
                .isAssignableFrom(jobType) || MavenModule.class.isAssignableFrom(jobType));
    }

    /**
     * Returns {@code true} if and only if this {@link DeploySource} is relevant to the job type
     *
     * @param jobType the type of job.
     * @return {@code true} if and only if this {@link DeploySource} is relevant to the job type
     */
    public boolean isApplicable(@CheckForNull Class<? extends AbstractProject> jobType) {
        return true;
    }

    /**
     * Returns {@code true} if and only if this {@link DeploySource} can operate from the specified origin.
     *
     * @param origin the origin.
     * @return {@code true} if and only if this {@link DeploySource} can operate from the specified origin.
     */
    public abstract boolean isSupported(@CheckForNull DeploySourceOrigin origin);

    /**
     * Returns {@code true} if and only if this {@link DeploySource} can operate from at least one of the specified
     * origins.
     *
     * @param origins the origins.
     * @return {@code true} if and only if this {@link DeploySource} can operate from at least one of the specified
     *         origins
     * @since 4.9
     */
    public boolean isSupported(@CheckForNull DeploySourceOrigin... origins) {
        for (DeploySourceOrigin origin : origins) {
            if (isSupported(origin)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if and only if this {@link DeploySource} can return files.
     *
     * @return {@code true} if and only if this {@link DeploySource} can return files.
     * @since 4.3
     */
    public boolean isFileSource() {
        return true;
    }

    /**
     * Returns {@code true} if and only if this {@link DeploySource} can return directories.
     *
     * @return {@code true} if and only if this {@link DeploySource} can return directories.
     * @since 4.3
     */
    public boolean isDirectorySource() {
        return false;
    }

    /**
     * If the stapler request ancestor is a Run or one of the deploy now actions, will return {@code null},
     * otherwise returns the run.
     *
     * @return the run ancestor of the stapler request or {@code null} if there is none.
     */
    @CheckForNull
    protected Run findRun() {
        StaplerRequest request = Stapler.getCurrentRequest();
        if (request == null) {
            return null;
        }
        List<Ancestor> ancestors = request.getAncestors();
        for (int i = ancestors.size() - 1; i >= 0; i--) {
            Ancestor a = ancestors.get(i);
            Object object = a.getObject();
            if (object instanceof DeployNowRunAction) {
                return ((DeployNowRunAction) object).getOwner();
            }
            if (object instanceof DeployNowProjectAction) {
                return CapabilitiesResolver.getLastDeployableBuild((((DeployNowProjectAction) object).getOwner()));
            }
            if (object instanceof Run) {
                return (Run) object;
            }
            if (object instanceof Job) {
                return CapabilitiesResolver.getLastDeployableBuild((Job) object);
            }
        }

        return null;
    }

    /**
     * If the stapler request ancestor is a Run or one of the deploy now actions, will return {@code null},
     * otherwise returns the job.
     *
     * @return the job ancestor of the stapler request or {@code null} if there is none.
     */
    @CheckForNull
    protected Job findJob() {
        StaplerRequest request = Stapler.getCurrentRequest();
        if (request == null) {
            return null;
        }
        List<Ancestor> ancestors = request.getAncestors();
        for (int i = ancestors.size() - 1; i >= 0; i--) {
            Ancestor a = ancestors.get(i);
            Object object = a.getObject();
            if (object instanceof DeployNowRunAction) {
                return null;
            }
            if (object instanceof DeployNowProjectAction) {
                return null;
            }
            if (object instanceof Run) {
                return null;
            }
            if (object instanceof Job) {
                return ((Job) object);
            }
        }

        return null;
    }

    /**
     * Create a new default instance of the {@link DeploySource}
     *
     * @return the new instance.
     */
    public DeploySource newInstance() {
        throw new UnsupportedOperationException();
    }

    /**
     * Delegates the final path validation to the {@link DeployTarget}.
     *
     * @param targetDescriptorId the class name of the {@link DeployTarget} to delegate to.
     * @param path               the path we believe to be OK.
     * @return the results of validation.
     * @since 4.3
     */
    protected FormValidation delegatePathValidationToTarget(@CheckForNull String pathName,
                                                            @CheckForNull String targetDescriptorId,
                                                            @CheckForNull FilePath path)
            throws IOException, InterruptedException {
        if (path != null) {
            Descriptor o = Jenkins.getInstance().getDescriptorByName(targetDescriptorId);
            if (o instanceof DeployTargetDescriptor) {
                final DeployTargetDescriptor d = (DeployTargetDescriptor) o;
                if (d.clazz.getName().equals(targetDescriptorId)) {
                    return d.validateFilePath(pathName, path);
                }
            }
        }
        return FormValidation.ok();
    }
}
