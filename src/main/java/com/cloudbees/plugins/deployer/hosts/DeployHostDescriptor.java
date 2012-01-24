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

package com.cloudbees.plugins.deployer.hosts;

import com.cloudbees.plugins.deployer.resolvers.CapabilitiesResolver;
import com.cloudbees.plugins.deployer.sources.DeploySourceOrigin;
import com.cloudbees.plugins.deployer.targets.DeployTarget;
import com.cloudbees.plugins.deployer.targets.DeployTargetDescriptor;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Run;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The base class for {@link DeployHost} descriptors.
 *
 * @since 4.0
 */
public abstract class DeployHostDescriptor<S extends DeployHost<S, T>, T extends DeployTarget<T>>
        extends Descriptor<DeployHost<S, T>> {

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unused")
    protected DeployHostDescriptor() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unused")
    protected DeployHostDescriptor(Class<? extends DeployHost<S, T>> clazz) {
        super(clazz);
    }

    /**
     * Returns the subclass of {@link DeployTarget} that the {@link DeployHost} is restricted to.
     *
     * @return the subclass of {@link DeployTarget} that the {@link DeployHost} is restricted to.
     */
    @NonNull
    public abstract Class<T> getDeployTargetClass();

    /**
     * {@inheritDoc}
     */
    @NonNull
    @SuppressWarnings({"unused", "unchecked"}) // used by stapler
    public DeployTargetDescriptor<T> getDeployTargetDescriptor() {
        return (DeployTargetDescriptor<T>) Hudson.getInstance().getDescriptorOrDie(getDeployTargetClass());
    }

    /**
     * Creates a default {@link DeployHost} instance from the specified project considering the specified origins.
     *
     * @param owner   the project.
     * @param origins the origins.
     * @return the best guess at a default deployment from the specified project when using the specified origins or
     *         {@code null} if there is no default.
     */
    @CheckForNull
    public S createDefault(@CheckForNull AbstractProject<?, ?> owner, @CheckForNull Set<DeploySourceOrigin> origins) {
        return createDefault(CapabilitiesResolver.of(owner).getLastSuccessfulBuild(owner), origins);
    }

    /**
     * Creates a default {@link DeployHost} instance from the specified project run considering the specified origins.
     *
     * @param run     the project run.
     * @param origins the origins.
     * @return the best guess at a default deployment from the specified project run when using the specified origins or
     *         {@code null} if there is no default.
     */
    @CheckForNull
    public abstract S createDefault(@CheckForNull Run<?, ?> run, @CheckForNull Set<DeploySourceOrigin> origins);

    /**
     * Update a default {@link DeployHost} instance from the specified project considering the specified origins,
     * filling in any information missing from the previous default but which now has a better guess.
     *
     * @param owner    the project run.
     * @param origins  the origins.
     * @param template the previous default.
     * @return the best guess at a default deployment from the specified project when using the specified origins or
     *         {@code null} if there is no default.
     */
    @CheckForNull
    public S updateDefault(@CheckForNull AbstractProject<?, ?> owner, @CheckForNull Set<DeploySourceOrigin> origins,
                           @CheckForNull S template) {
        return updateDefault(CapabilitiesResolver.of(owner).getLastSuccessfulBuild(owner), origins, template);
    }

    /**
     * Update a default {@link DeployHost} instance from the specified project run considering the specified origins,
     * filling in any information missing from the previous default but which now has a better guess.
     *
     * @param run      the project run.
     * @param origins  the origins.
     * @param template the previous default.
     * @return the best guess at a default deployment from the specified project run when using the specified origins or
     *         {@code null} if there is no default.
     */
    @CheckForNull
    public S updateDefault(@CheckForNull Run<?, ?> run, @CheckForNull Set<DeploySourceOrigin> origins,
                           @CheckForNull S template) {
        return createDefault(run, origins);
    }

    /**
     * Returns {@code true} if and only if this host is valid for the specified origins and job type.
     *
     * @param origins the origins
     * @param jobType the job type.
     * @return {@code true} if and only if this host is valid for the specified origins and job type.
     * @since 4.3
     */
    public boolean isSupported(@NonNull Set<DeploySourceOrigin> origins,
                               @CheckForNull Class<? extends AbstractProject> jobType) {
        return !getDeployTargetDescriptor().getDeploySourceDescriptors(origins, jobType).isEmpty();
    }

    /**
     * Returns {@code true} if and only if this host is valid for the specified origins and run.
     *
     * @param origins the origins
     * @param run the run.
     * @return {@code true} if and only if this host is valid for the specified origins and run.
     * @since 4.12
     */
    public boolean isSupported(@NonNull Set<DeploySourceOrigin> origins,
                               @CheckForNull Run<?, ?> run) {
        Class<? extends AbstractProject<?, ?>> jobType;
        if (run != null && run.getParent() instanceof AbstractProject) {
            jobType = (Class<? extends AbstractProject<?, ?>>) run.getParent().getClass();
        } else {
            jobType = null;
        }
        return isSupported(origins, jobType);
    }

    /**
     * Returns all the {@link DeployHost} descriptors.
     *
     * @return all the {@link DeployHost} descriptors.
     */
    public static ExtensionList<? extends DeployHostDescriptor<?, ?>> all() {
        return Jenkins.getInstance().getDescriptorList(DeployHost.class);
    }

    /**
     * Returns the descriptors that are supported for the specified origins and job type.
     *
     * @param origins the origins
     * @param jobType the job type.
     * @return the descriptors that are supported for the specified origins and job type.
     * @since 4.3
     */
    public static List<DeployHostDescriptor<?, ?>> allSupported(@NonNull Set<DeploySourceOrigin> origins,
                                                                @CheckForNull Class<? extends AbstractProject>
                                                                        jobType) {
        List<DeployHostDescriptor<?, ?>> result = new ArrayList<DeployHostDescriptor<?, ?>>();
        for (DeployHostDescriptor<?, ?> d : all()) {
            if (d.isSupported(origins, jobType)) {
                result.add(d);
            }
        }
        return result;
    }
}
