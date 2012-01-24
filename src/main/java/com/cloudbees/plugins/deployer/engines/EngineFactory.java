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

import com.cloudbees.plugins.deployer.exceptions.DeployException;
import com.cloudbees.plugins.deployer.hosts.DeployHost;
import com.cloudbees.plugins.deployer.sources.DeploySourceOrigin;
import com.cloudbees.plugins.deployer.targets.DeployTarget;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.BuildListener;
import hudson.model.Item;
import org.acegisecurity.Authentication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

/**
 * @author stephenc
 * @since 06/12/2012 10:16
 */
public abstract class EngineFactory<S extends DeployHost<S, T>, T extends DeployTarget<T>>
        extends AbstractDescribableImpl<EngineFactory<S, T>> {
    private EngineConfiguration<S, T> configuration;

    protected EngineFactory(@NonNull S configuration) {
        configuration.getClass(); // throw NPE if null
        this.configuration = new EngineConfiguration<S, T>(configuration);
    }

    @Override
    public EngineFactoryDescriptor<S, T> getDescriptor() {
        return (EngineFactoryDescriptor<S, T>) super.getDescriptor();
    }

    @NonNull
    public EngineFactory<S, T> withCredentials(@Nullable Item scope, Authentication... authentications) {
        configuration = configuration.withDeployScope(scope)
                .withDeployAuthentications(new ArrayList<Authentication>(Arrays.asList(authentications)));
        return this;
    }

    @NonNull
    public EngineFactory<S, T> withCredentials(@Nullable Item scope,
                                               @CheckForNull Collection<Authentication> authentications) {
        configuration = configuration.withDeployScope(scope)
                .withDeployAuthentications(authentications == null
                        ? new ArrayList<Authentication>()
                        : new ArrayList<Authentication>(authentications));
        return this;
    }

    @NonNull
    public EngineFactory<S, T> from(@NonNull AbstractBuild<?, ?> build, DeploySourceOrigin... sources) {
        build.getClass(); // throw NPE if null
        configuration = configuration
                .withBuild(build)
                .withSources(new LinkedHashSet<DeploySourceOrigin>(Arrays.asList(sources)));
        return this;
    }

    @NonNull
    public EngineFactory<S, T> from(@NonNull AbstractBuild<?, ?> build, Collection<DeploySourceOrigin> sources) {
        build.getClass(); // throw NPE if null
        configuration = configuration
                .withBuild(build)
                .withSources(sources == null
                        ? Collections.<DeploySourceOrigin>emptySet()
                        : new LinkedHashSet<DeploySourceOrigin>(sources));
        return this;
    }

    @NonNull
    public EngineFactory<S, T> withListener(@NonNull BuildListener listener) {
        listener.getClass(); // throw NPE if null
        configuration = configuration.withListener(listener);
        return this;
    }

    @NonNull
    public EngineFactory<S, T> withLauncher(Launcher launcher) {
        launcher.getClass(); // throw NPE if null
        configuration = configuration.withLauncher(launcher);
        return this;
    }

    @NonNull
    public abstract Engine<S, T> build() throws DeployException;

    public EngineConfiguration<S, T> getConfiguration() {
        return configuration;
    }

}
