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

import com.cloudbees.plugins.deployer.engines.Engine;
import com.cloudbees.plugins.deployer.hosts.DeployHost;
import com.cloudbees.plugins.deployer.hosts.DeployHostDescriptor;
import com.cloudbees.plugins.deployer.hosts.DeployHostsContext;
import com.cloudbees.plugins.deployer.resolvers.CapabilitiesResolver;
import com.cloudbees.plugins.deployer.sources.DeploySource;
import com.cloudbees.plugins.deployer.sources.DeploySourceDescriptor;
import com.cloudbees.plugins.deployer.sources.DeploySourceOrigin;
import com.cloudbees.plugins.deployer.targets.DeployTarget;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A publisher that can deploy applications to RUN@cloud
 */
public class DeployPublisher extends Notifier {

    private boolean deployIfUnstable;

    private transient DeployConfiguration configuration;

    private /*final*/ List<? extends DeployHost<?, ?>> hosts;

    @Deprecated
    public DeployPublisher(DeployConfiguration configuration) {
        this(Arrays.asList(configuration.toDeploySet()), false);
    }

    @Deprecated
    public DeployPublisher(DeployConfiguration configuration, boolean deployIfUnstable) {
        this(Arrays.asList(configuration.toDeploySet()), deployIfUnstable);
    }

    @Deprecated
    public DeployPublisher(List<? extends DeployHost<?, ?>> hosts) {
        this(hosts, false);
    }

    @DataBoundConstructor
    public DeployPublisher(List<? extends DeployHost<?, ?>> hosts, boolean deployIfUnstable) {
        this.hosts = new ArrayList<DeployHost<?, ?>>(hosts == null ? Collections.<DeployHost<?, ?>>emptySet() : hosts);
        this.deployIfUnstable = deployIfUnstable;
    }

    public boolean isDeployIfUnstable() {
        return deployIfUnstable;
    }

    public DeployConfiguration getConfiguration() {
        return configuration;
    }

    public List<? extends DeployHost<?, ?>> getHosts() {
        return hosts;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        if (build.getResult().isWorseThan(deployIfUnstable ? Result.UNSTABLE : Result.SUCCESS)) {
            listener.getLogger().println("[cloudbees-deployer] Skipping deployment as build result is "
                    + build.getResult().toString());
            return true;
        }
        try {
            for (DeployHost<? extends DeployHost<?, ?>, ? extends DeployTarget<?>> set : hosts) {
                if (!Engine.create(set)
                        .withCredentials(build.getProject(), ACL.SYSTEM)
                        .from(build, DeploySourceOrigin.WORKSPACE, DeploySourceOrigin.RUN)
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

    protected Object readResolve() throws ObjectStreamException {
        if (configuration != null && hosts == null) {
            hosts = new ArrayList<DeployHost<?, ?>>(Arrays.asList(configuration.toDeploySet()));
            configuration = null;
        } else if (hosts == null) {
            hosts = new ArrayList<DeployHost<?, ?>>();
        }
        return this;
    }


    /**
     * Our descriptor
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            if (CapabilitiesResolver.of(jobType).isBuilderApplicable()) {
                // RM-2071 the publisher is only applicable if we have at least one source
                Set<DeploySourceOrigin> have = new HashSet<DeploySourceOrigin>();
                Set<DeploySourceOrigin> want = new HashSet<DeploySourceOrigin>(
                        Arrays.asList(DeploySourceOrigin.WORKSPACE, DeploySourceOrigin.RUN)
                );
                for (Descriptor<DeploySource> d : Hudson.getInstance().getDescriptorList(DeploySource.class)) {
                    if (d instanceof DeploySourceDescriptor) {
                        DeploySourceDescriptor descriptor = (DeploySourceDescriptor) d;
                        if (descriptor.isApplicable(jobType)) {
                            for (Iterator<DeploySourceOrigin> iterator = want.iterator(); iterator.hasNext(); ) {
                                DeploySourceOrigin origin = iterator.next();
                                if (descriptor.isSupported(origin)) {
                                    have.add(origin);
                                    iterator.remove();
                                }
                            }
                            if (want.isEmpty()) {
                                break;
                            }
                        }
                    }
                }
                // the publisher is only applicable if we have at least one host service for the sources we have
                if (!have.isEmpty()) {
                    for (DeployHostDescriptor d : DeployHostDescriptor.all()) {
                        if (d.isSupported(have, jobType)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.DeployPublisher_DisplayName();
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
                                                                            DeployPublisher instance) {
            final CapabilitiesResolver resolver = CapabilitiesResolver.of(it);
            return new DeployHostsContext<AbstractProject<?, ?>>(it,
                    instance == null
                            ? DeployHost.createDefaults(it, resolver.getPublisherSources(it))
                            : instance.getHosts(),
                    it,
                    resolver.getPublisherSources(it),
                    resolver.isPublisherFromWorkspace(),
                    false);
        }

    }

}
