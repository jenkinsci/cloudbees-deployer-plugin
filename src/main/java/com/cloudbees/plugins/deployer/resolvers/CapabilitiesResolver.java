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

package com.cloudbees.plugins.deployer.resolvers;

import com.cloudbees.plugins.deployer.sources.DeploySourceOrigin;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.Jenkins;

import java.util.Collections;
import java.util.Set;

/**
 * Provides information about the capabilities that should be exposed for different project types.
 * If there is a specific project type which should have its own tweaks (e.g. promoted-builds)
 * then it can implement an extension of this type and change the effective behaviour.
 */
public abstract class CapabilitiesResolver implements ExtensionPoint {

    /**
     * Lazy singleton holder.
     */
    private static class ResourceHolder {
        /**
         * Default capabilities resolver.
         */
        private static final DefaultCapabilitiesResolver defaultResolver = new DefaultCapabilitiesResolver();
    }

    /**
     * Returns {@code true} if this resolver applies to the specific job type.
     *
     * @param jobType the specific job type.
     * @return {@code true} if this resolver applies to the specific job type.
     */
    protected abstract boolean knows(@NonNull Class<? extends AbstractProject> jobType);

    /**
     * Return the set of sources to be used by {@link com.cloudbees.plugins.deployer.DeployBuilder}.
     *
     * @param project the project.
     * @return the set of sources to be used by {@link com.cloudbees.plugins.deployer.DeployBuilder}.
     */
    @NonNull
    public abstract Set<DeploySourceOrigin> getBuilderSources(@CheckForNull AbstractProject<?, ?> project);

    /**
     * Return the set of sources to be used by {@link com.cloudbees.plugins.deployer.DeployPublisher}.
     *
     * @param project the project.
     * @return the set of sources to be used by {@link com.cloudbees.plugins.deployer.DeployPublisher}.
     */
    @NonNull
    public abstract Set<DeploySourceOrigin> getPublisherSources(@CheckForNull AbstractProject<?, ?> project);

    /**
     * Return the set of sources to be used by {@link com.cloudbees.plugins.deployer.DeployNowRunAction}.
     *
     * @param project the project.
     * @return the set of sources to be used by {@link com.cloudbees.plugins.deployer.DeployNowRunAction}.
     */
    @NonNull
    public abstract Set<DeploySourceOrigin> getInstantSources(@CheckForNull AbstractProject<?, ?> project);

    /**
     * Return {@code true} if and only if {@link com.cloudbees.plugins.deployer.DeployBuilder} is allowed to apply to
     * project types known by this resolver.
     *
     * @return {@code true} if and only if {@link com.cloudbees.plugins.deployer.DeployBuilder} is allowed to apply to
     *         project types known by this resolver.
     */
    public boolean isBuilderApplicable() {
        return true;
    }

    /**
     * Return {@code true} if and only if {@link com.cloudbees.plugins.deployer.DeployPublisher} is allowed to apply to
     * project types known by this resolver.
     *
     * @return {@code true} if and only if {@link com.cloudbees.plugins.deployer.DeployPublisher} is allowed to apply to
     *         project types known by this resolver.
     */
    public boolean isPublisherApplicable() {
        return true;
    }

    /**
     * Return {@code true} if and only if {@link com.cloudbees.plugins.deployer.DeployNowRunAction} is allowed to
     * apply to project types known by this resolver.
     *
     * @return {@code true} if and only if {@link com.cloudbees.plugins.deployer.DeployNowRunAction} is allowed to
     *         apply to project types known by this resolver.
     */
    public boolean isInstantApplicable() {
        return true;
    }

    /**
     * Return {@code true} if and only if {@link com.cloudbees.plugins.deployer.DeployBuilder} is allowed to resolve
     * deployable artifacts from the job workspace.
     *
     * @return {@code true} if and only if {@link com.cloudbees.plugins.deployer.DeployBuilder} is allowed to resolve
     *         deployable artifacts from the job workspace.
     */
    public boolean isBuilderFromWorkspace() {
        return true;
    }

    /**
     * Return {@code true} if and only if {@link com.cloudbees.plugins.deployer.DeployPublisher} is allowed to resolve
     * deployable artifacts from the job workspace.
     *
     * @return {@code true} if and only if {@link com.cloudbees.plugins.deployer.DeployPublisher} is allowed to resolve
     *         deployable artifacts from the job workspace.
     */
    public boolean isPublisherFromWorkspace() {
        return true;
    }

    /**
     * Returns the last successful build of the specified project.
     *
     * @param project the specified project.
     * @return the last successful build or {@code null} if no such build exists.
     */
    @CheckForNull
    public abstract Run<?, ?> getLastSuccessfulBuild(@CheckForNull AbstractProject<?, ?> project);

    /**
     * Returns the {@link CapabilitiesResolver} for the specified job type.
     *
     * @param jobType the specified job type.
     * @return the {@link CapabilitiesResolver}
     */
    @NonNull
    public static CapabilitiesResolver of(@CheckForNull Class<? extends AbstractProject> jobType) {
        if (jobType != null) {
            for (CapabilitiesResolver capabilitiesResolver : Jenkins.getInstance()
                    .getExtensionList(CapabilitiesResolver.class)) {
                try {
                    if (capabilitiesResolver.knows(jobType)) {
                        return capabilitiesResolver;
                    }
                } catch (NoClassDefFoundError e) {
                    // ignore
                } catch (LinkageError e) {
                    // ignore
                }
            }
        }
        return ResourceHolder.defaultResolver;
    }

    /**
     * Returns the most recent successful or stable build of the specified job.
     *
     * @param job the specified job.
     * @return the most recent successful or stable build of the specified job.
     */
    @CheckForNull
    public static Run<?,?> getLastDeployableBuild(@CheckForNull Job<?, ?> job) {
        if (job == null) {
            return null;
        }
        final Run<?,?> run = job.getLastSuccessfulBuild();
        // Some versions of Jenkins break this invariant and have a stable but no successful build
        return run != null ? run : job.getLastStableBuild();
    }

    /**
     * Returns the {@link CapabilitiesResolver} for the specified project.
     *
     * @param project the specified project.
     * @return the {@link CapabilitiesResolver}
     */
    @NonNull
    public static CapabilitiesResolver of(@CheckForNull AbstractProject<?, ?> project) {
        return of(project == null ? AbstractProject.class : project.getClass());
    }

    /**
     * Default implementation.
     */
    private static class DefaultCapabilitiesResolver extends CapabilitiesResolver {

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean knows(@NonNull Class<? extends AbstractProject> jobType) {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public Set<DeploySourceOrigin> getBuilderSources(@CheckForNull AbstractProject<?, ?> project) {
            return Collections.singleton(DeploySourceOrigin.WORKSPACE);
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public Set<DeploySourceOrigin> getPublisherSources(@CheckForNull AbstractProject<?, ?> project) {
            return DeploySourceOrigin.all();
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public Set<DeploySourceOrigin> getInstantSources(@CheckForNull AbstractProject<?, ?> project) {
            return Collections.singleton(DeploySourceOrigin.RUN);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Run<?, ?> getLastSuccessfulBuild(AbstractProject<?, ?> project) {
            return getLastDeployableBuild((AbstractProject) project);
        }
    }
}
