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

import com.cloudbees.plugins.deployer.sources.DeploySourceOrigin;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.AbstractProject;
import hudson.model.Job;
import net.jcip.annotations.Immutable;

import java.util.List;
import java.util.Set;

/**
 * @author stephenc
 * @since 14/12/2012 10:11
 */
@Immutable
public class DeployHostsContext<T> {

    @NonNull
    private final T it;

    @NonNull
    private final List<? extends DeployHost<?, ?>> hosts;

    @CheckForNull
    private final Job<?, ?> project;

    @NonNull
    private final Set<DeploySourceOrigin> origins;

    private final boolean fromWorkspace;

    private final boolean usersAuth;

    public DeployHostsContext(@NonNull T it, @NonNull List<? extends DeployHost<?, ?>> hosts,
                              @CheckForNull Job<?, ?> project,
                              @NonNull Set<DeploySourceOrigin> origins, boolean fromWorkspace, boolean usersAuth) {
        it.getClass(); // throw NPE if null;
        hosts.getClass(); // throw NPE if null
        origins.getClass(); // throw NPE if null
        this.it = it;
        this.hosts = hosts;
        this.project = project;
        this.origins = origins;
        this.fromWorkspace = fromWorkspace;
        this.usersAuth = usersAuth;
    }

    @SuppressWarnings("unused") // used by stapler
    public boolean isUsersAuth() {
        return usersAuth;
    }

    @CheckForNull
    @SuppressWarnings("unused") // used by stapler
    public Job<?, ?> getProject() {
        return project;
    }

    @NonNull
    @SuppressWarnings("unused") // used by stapler
    public Set<DeploySourceOrigin> getOrigins() {
        return origins;
    }

    @NonNull
    @SuppressWarnings("unused") // used by stapler
    public T getIt() {
        return it;
    }

    @SuppressWarnings("unused") // used by stapler
    public boolean isFromWorkspace() {
        return fromWorkspace;
    }

    @SuppressWarnings("unused") // used by stapler
    @NonNull
    public List<? extends DeployHost<?, ?>> getHosts() {
        return hosts;
    }

    /**
     * Convenience method to make setting up the hetero-list easier.
     *
     * @return the {@link DeployHost} descriptors.
     */
    @SuppressWarnings("unused") // used by stapler
    public List<DeployHostDescriptor<?, ?>> getDescriptors() {
        return DeployHostDescriptor.allSupported(origins, project instanceof AbstractProject
                ? ((AbstractProject) project).getClass()
                : null);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DeployHostsContext");
        sb.append("{it=").append(it);
        sb.append(", hosts=").append(hosts);
        sb.append(", project=").append(project);
        sb.append(", origins=").append(origins);
        sb.append(", fromWorkspace=").append(fromWorkspace);
        sb.append(", usersAuth=").append(usersAuth);
        sb.append('}');
        return sb.toString();
    }
}
