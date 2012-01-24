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

package com.cloudbees.plugins.deployer.engines;

import com.cloudbees.plugins.deployer.hosts.DeployHost;
import com.cloudbees.plugins.deployer.sources.DeploySourceOrigin;
import com.cloudbees.plugins.deployer.targets.DeployTarget;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Item;
import net.jcip.annotations.Immutable;
import org.acegisecurity.Authentication;

import javax.annotation.CheckForNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents all the configuration requirements of an {@link Engine}
 */
@Immutable
public class EngineConfiguration<S extends DeployHost<S, T>, T extends DeployTarget<T>> {

    /**
     * The scope from which credentials can be retrieved.
     */
    @CheckForNull
    private final Item deployScope;

    /**
     * The authentications with which credentials can be retrieved.
     */
    @CheckForNull
    private final List<Authentication> deployAuthentications;

    /**
     * The build to be deployed.
     */
    @CheckForNull
    private final AbstractBuild<?, ?> build;

    /**
     * The configuration to deploy.
     */
    @NonNull
    private final S configuration;

    /**
     * A launcher which can be used for doing the deployment.
     */
    @CheckForNull
    private final Launcher launcher;

    /**
     * A listener to record the deployment on.
     */
    @CheckForNull
    private final BuildListener listener;

    /**
     * The sources from which {@link com.cloudbees.plugins.deployer.deployables.Deployable} can be resolved.
     */
    @CheckForNull
    private final Set<DeploySourceOrigin> sources;

    public EngineConfiguration(@NonNull S configuration) {
        configuration.getClass(); // throw NPE if null
        this.configuration = configuration;
        deployScope = null;
        deployAuthentications = null;
        build = null;
        launcher = null;
        listener = null;
        sources = null;
    }

    private EngineConfiguration(@CheckForNull AbstractBuild<?, ?> build, @CheckForNull Item deployScope,
                                @CheckForNull List<Authentication> deployAuthentications, @NonNull S configuration,
                                @CheckForNull Launcher launcher, @CheckForNull BuildListener listener,
                                @CheckForNull Set<DeploySourceOrigin> sources) {
        configuration.getClass(); // throw NPE if null
        this.build = build;
        this.deployScope = deployScope;
        this.deployAuthentications = deployAuthentications == null
                ? null
                : Collections.unmodifiableList(new ArrayList<Authentication>(deployAuthentications));
        this.configuration = configuration;
        this.launcher = launcher;
        this.listener = listener;
        this.sources =
                sources == null ? null : Collections.unmodifiableSet(new LinkedHashSet<DeploySourceOrigin>(sources));
    }

    @CheckForNull
    public AbstractBuild<?, ?> getBuild() {
        return build;
    }

    @NonNull
    public EngineConfiguration<S, T> withBuild(@CheckForNull AbstractBuild<?, ?> build) {
        return new EngineConfiguration<S, T>(build, deployScope, deployAuthentications, configuration, launcher,
                listener,
                sources);
    }

    @NonNull
    public S getConfiguration() {
        return configuration;
    }

    @CheckForNull
    public List<Authentication> getDeployAuthentications() {
        return deployAuthentications;
    }

    @NonNull
    public EngineConfiguration<S, T> withDeployAuthentications(
            @CheckForNull List<Authentication> deployAuthentications) {
        return new EngineConfiguration<S, T>(build, deployScope, deployAuthentications, configuration, launcher,
                listener,
                sources);
    }

    @CheckForNull
    public Item getDeployScope() {
        return deployScope;
    }

    @NonNull
    public EngineConfiguration<S, T> withDeployScope(@CheckForNull Item deployScope) {
        return new EngineConfiguration<S, T>(build, deployScope, deployAuthentications, configuration, launcher,
                listener,
                sources);
    }

    @CheckForNull
    public Launcher getLauncher() {
        return launcher;
    }

    @NonNull
    public EngineConfiguration<S, T> withLauncher(@CheckForNull Launcher launcher) {
        return new EngineConfiguration<S, T>(build, deployScope, deployAuthentications, configuration, launcher,
                listener,
                sources);
    }

    @CheckForNull
    public BuildListener getListener() {
        return listener;
    }

    @NonNull
    public EngineConfiguration<S, T> withListener(@CheckForNull BuildListener listener) {
        return new EngineConfiguration<S, T>(build, deployScope, deployAuthentications, configuration, launcher,
                listener,
                sources);
    }

    @CheckForNull
    public Set<DeploySourceOrigin> getSources() {
        return sources;
    }

    @NonNull
    public EngineConfiguration<S, T> withSources(@CheckForNull Set<DeploySourceOrigin> sources) {
        return new EngineConfiguration<S, T>(build, deployScope, deployAuthentications, configuration, launcher,
                listener,
                sources);
    }
}
