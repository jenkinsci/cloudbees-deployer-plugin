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
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A build step that can deploy applications to RUN@cloud
 */
public class DeployBuilder extends Builder {

    @Deprecated
    @SuppressWarnings("deprecation")
    private transient DeployConfiguration configuration;

    private /*final*/ List<? extends DeployHost<?, ?>> hosts;

    @Deprecated
    @SuppressWarnings("deprecation")
    public DeployBuilder(DeployConfiguration configuration) {
        this(Arrays.asList(configuration.toDeploySet()));
    }

    @DataBoundConstructor
    public DeployBuilder(List<? extends DeployHost<?, ?>> hosts) {
        this.hosts = new ArrayList<DeployHost<?, ?>>(hosts == null ? Collections.<DeployHost<?, ?>>emptySet() : hosts);
    }

    @Deprecated
    @SuppressWarnings("deprecation")
    public DeployConfiguration getConfiguration() {
        return configuration;
    }

    public List<? extends DeployHost<?, ?>> getHosts() {
        return hosts;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        try {
            for (DeployHost<? extends DeployHost<?, ?>, ? extends DeployTarget<?>> set : hosts) {
                if (!Engine.create(set)
                        .withCredentials(build.getProject(), ACL.SYSTEM)
                        .from(build, DeploySourceOrigin.WORKSPACE)
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

    @SuppressWarnings("deprecation")
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
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            if (CapabilitiesResolver.of(jobType).isBuilderApplicable()) {
                // RM-2071 the builder is only applicable if we have at least one source
                boolean haveSource = false;
                for (Descriptor<DeploySource> d : Hudson.getInstance().getDescriptorList(DeploySource.class)) {
                    if (d instanceof DeploySourceDescriptor) {
                        DeploySourceDescriptor descriptor = (DeploySourceDescriptor) d;
                        if (descriptor.isApplicable(jobType) && descriptor.isSupported(DeploySourceOrigin.WORKSPACE)) {
                            haveSource = true;
                            break;
                        }
                    }
                }
                // the builder is only applicable if we have at least one host service
                if (haveSource) {
                    for (DeployHostDescriptor d : DeployHostDescriptor.all()) {
                        if (d.isSupported(Collections.singleton(DeploySourceOrigin.WORKSPACE), jobType)) {
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
            final CapabilitiesResolver resolver = CapabilitiesResolver.of(it);
            return new DeployHostsContext<AbstractProject<?, ?>>(it,
                    instance == null
                            ? DeployHost.createDefaults(it, resolver.getBuilderSources(it))
                            : instance.getHosts(),
                    it,
                    resolver.getBuilderSources(it),
                    resolver.isBuilderFromWorkspace(),
                    false);
        }

    }

}
