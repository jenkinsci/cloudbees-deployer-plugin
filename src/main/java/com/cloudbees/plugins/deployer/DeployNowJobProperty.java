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

import com.cloudbees.plugins.deployer.hosts.DeployHost;
import com.cloudbees.plugins.deployer.hosts.DeployHostsContext;
import com.cloudbees.plugins.deployer.resolvers.CapabilitiesResolver;
import com.cloudbees.plugins.deployer.sources.DeploySourceOrigin;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Hudson;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.TransientProjectActionFactory;
import hudson.util.ExceptionCatchingThreadFactory;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Holds the default configuration for deploying now.
 */
public class DeployNowJobProperty extends JobProperty<AbstractProject<?, ?>> {

    private boolean oneClickDeploy;

    @Deprecated
    @SuppressWarnings("deprecation")
    private transient DeployConfiguration configuration;

    private List<? extends DeployHost<?, ?>> hosts;

    @Deprecated
    @SuppressWarnings("deprecation")
    public DeployNowJobProperty(boolean oneClickDeploy, DeployConfiguration configuration) {
        this(oneClickDeploy, Arrays.asList(configuration.toDeploySet()));
    }

    @DataBoundConstructor
    public DeployNowJobProperty(boolean oneClickDeploy, List<? extends DeployHost<?, ?>> hosts) {
        this.hosts = new ArrayList<DeployHost<?, ?>>(hosts == null ? Collections.<DeployHost<?, ?>>emptySet() : hosts);
        this.oneClickDeploy = oneClickDeploy;
    }

    static void submit(Runnable task) {
        ((DescriptorImpl) Hudson.getInstance().getDescriptor(DeployNowJobProperty.class)).deployNowPool.submit(
                task);
    }

    public boolean isOneClickDeploy() {
        return oneClickDeploy;
    }

    public void setOneClickDeploy(boolean oneClickDeploy) {
        this.oneClickDeploy = oneClickDeploy;
    }

    @Deprecated
    @SuppressWarnings("deprecation")
    public DeployConfiguration getConfiguration() {
        return configuration;
    }

    public List<? extends DeployHost<?, ?>> getHosts() {
        return hosts == null
                ? DeployHost.createDefaults(owner, Collections.singleton(DeploySourceOrigin.RUN))
                : hosts;
    }

    public void setHosts(List<? extends DeployHost<?, ?>> hosts) {
        this.hosts = hosts;
    }

    @Override
    public Collection<? extends Action> getJobActions(AbstractProject<?, ?> job) {
        return Arrays.asList(new DeployNowProjectAction(job));
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

    @Extension
    public static class DescriptorImpl extends JobPropertyDescriptor {

        public final ExecutorService deployNowPool =
                new ThreadPoolExecutor(0, 2, 5L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
                        new NamedThreadFactory("DeployNowWorkers",
                                new ExceptionCatchingThreadFactory(
                                        Executors.defaultThreadFactory())));

        @Override
        public String getDisplayName() {
            return Messages.DeployNowJobProperty_DisplayName();
        }

        @Override
        public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return super.newInstance(req, formData);
        }

        public DeployNowJobProperty getInstance(AbstractProject<?, ?> project) {
            DeployNowJobProperty property = project.getProperty(DeployNowJobProperty.class);
            if (property != null) {
                return property;
            }
            return new DeployNowJobProperty(false,
                    DeployHost.createDefaults(project, CapabilitiesResolver.of(project).getInstantSources(project)));
        }

        public Set<DeploySourceOrigin> getSources() {
            AbstractProject project = Stapler.getCurrentRequest().findAncestorObject(AbstractProject.class);
            if (project != null) {
                return CapabilitiesResolver.of(project).getInstantSources(project);
            }
            return Collections.singleton(DeploySourceOrigin.RUN);
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
                                                                            DeployNowJobProperty instance) {
            final CapabilitiesResolver resolver = CapabilitiesResolver.of(it);
            return new DeployHostsContext<AbstractProject<?, ?>>(it,
                    instance == null
                            ? DeployHost.createDefaults(it, resolver.getInstantSources(it))
                            : instance.getHosts(),
                    it,
                    resolver.getInstantSources(it),
                    false,
                    true);
        }

    }

    /**
     * Needed to ensure the action is available if the job property is not present.
     */
    @Extension
    @SuppressWarnings("unused")
    public static class TransientProjectActionFactoryImpl extends TransientProjectActionFactory {

        @Override
        public Collection<? extends Action> createFor(AbstractProject target) {
            if (target.getProperty(DeployNowJobProperty.class) == null) {
                return Arrays.asList(new DeployNowProjectAction(target));
            }
            return Collections.emptySet();
        }

    }

}
