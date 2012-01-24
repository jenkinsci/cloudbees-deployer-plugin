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

import com.cloudbees.plugins.deployer.DeployBuilder;
import com.cloudbees.plugins.deployer.Messages;
import com.cloudbees.plugins.deployer.engines.Engine;
import com.cloudbees.plugins.deployer.hosts.DeployHost;
import com.cloudbees.plugins.deployer.hosts.DeployHostsContext;
import com.cloudbees.plugins.deployer.sources.DeploySourceOrigin;
import com.cloudbees.plugins.deployer.targets.DeployTarget;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.plugins.promoted_builds.Promotion;
import hudson.plugins.promoted_builds.PromotionProcess;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Promoted builds plugin needs different behaviour from the builder and also needs to remove the default builder and
 * publisher.
 */
@Extension(optional = true)
public class PromotionCapabilitiesResolver extends CapabilitiesResolver {

    private static boolean isPromotionProcess(@NonNull Class<? extends AbstractProject> jobType) {
        try {
            return innerIsPromotionProcess(jobType);
        } catch (NoClassDefFoundError e) {
            return false;
        } catch (LinkageError e) {
            return false;
        }
    }

    /**
     * Need to put this in a separate method as all the classes referenced in a method are loaded when the method
     * is loaded, so the catch clauses would never be in play.
     *
     * @param jobType
     * @return true if the class is a PromotionProcess.
     */
    private static boolean innerIsPromotionProcess(Class<? extends AbstractProject> jobType) {
        return PromotionProcess.class.isAssignableFrom(jobType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean knows(@NonNull Class<? extends AbstractProject> jobType) {
        return isPromotionProcess(jobType);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Set<DeploySourceOrigin> getBuilderSources(@CheckForNull AbstractProject<?, ?> project) {
        return Collections.singleton(DeploySourceOrigin.RUN);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Set<DeploySourceOrigin> getPublisherSources(@CheckForNull AbstractProject<?, ?> project) {
        return Collections.singleton(DeploySourceOrigin.RUN);
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
        return CapabilitiesResolver.getLastDeployableBuild(PromotionProcess.class.cast(project).getRootProject());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBuilderApplicable() {
        // we'll provide our own tailored builder.
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInstantApplicable() {
        // instant does not make sense in a promotion
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isPublisherApplicable() {
        // publisher is not relevant for promotion
        return false;
    }

    /**
     * Our promotion deploy builder
     */
    public static class DeployPromotionBuilder extends DeployBuilder {
        /**
         * Constructor.
         *
         * @param hosts our hosts
         */
        @DataBoundConstructor
        public DeployPromotionBuilder(List<DeployHost<?, ?>> hosts) {
            super(hosts);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            if (build instanceof Promotion) {
                build = ((Promotion) build).getTarget();
            }
            try {
                for (DeployHost<? extends DeployHost<?, ?>, ? extends DeployTarget<?>> set : getHosts()) {
                    if (!Engine.create(set)
                            .withCredentials(build.getProject(), ACL.SYSTEM)
                            .from(build, DeploySourceOrigin.RUN)
                            .withLauncher(launcher)
                            .withListener(listener)
                            .build()
                            .perform()) {
                        return false;
                    }
                }
            } catch (Throwable t) {
                // deployment failed - > fail the build
                t.printStackTrace(listener.getLogger());
                return false;
            }
            return true;
        }

        /**
         * Our descriptor.
         */
        @Extension(optional = true)
        public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return isPromotionProcess(jobType);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.DeployBuilder_DisplayName();
            }

            /**
             * Called by stapler to create the {@link com.cloudbees.plugins.deployer.hosts.DeployHostsContext}.
             *
             * @param it       the project.
             * @param instance the builder
             * @return the context.
             * @since 4.0
             */
            @SuppressWarnings("unused") // by stapler
            public DeployHostsContext<AbstractProject<?, ?>> createHostsContext(AbstractProject<?, ?> it,
                                                                                DeployBuilder instance) {
                Set<DeploySourceOrigin> origins = Collections.singleton(DeploySourceOrigin.RUN);
                return new DeployHostsContext<AbstractProject<?, ?>>(it,
                        instance == null
                                ? DeployHost.createDefaults(it, origins)
                                : instance.getHosts(),
                        it,
                        origins,
                        false,
                        false);
            }
        }
    }
}
