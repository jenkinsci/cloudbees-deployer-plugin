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

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.deployer.resolvers.CapabilitiesResolver;
import com.cloudbees.plugins.deployer.sources.DeploySourceOrigin;
import com.cloudbees.plugins.deployer.targets.DeployTarget;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.security.ACL;
import org.acegisecurity.Authentication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A set of {@link DeployTarget}s to be deployed.
 *
 * @since 4.0
 */
public abstract class DeployHost<S extends DeployHost<S, T>, T extends DeployTarget<T>>
        extends AbstractDescribableImpl<DeployHost<S, T>> {

    /**
     * The targets to deploy.
     */
    @NonNull
    private final List<T> targets;

    /**
     * Constructor.
     *
     * @param targets the targets to deploy.
     */
    protected DeployHost(@CheckForNull List<T> targets) {
        this.targets =
                Collections.unmodifiableList(new ArrayList<T>(targets == null ? Collections.<T>emptyList() : targets));
    }

    public static boolean isValid(List<? extends DeployHost<?, ?>> sets, Run<?, ?> owner,
                                  Authentication authentication) {
        for (DeployHost<?, ?> set : sets) {
            if (!set.isValid(owner, authentication)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValid(List<? extends DeployHost<?, ?>> sets, AbstractProject<?, ?> owner,
                                  Authentication authentication) {
        for (DeployHost<?, ?> set : sets) {
            if (!set.isValid(owner, authentication)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the targets to deploy.
     *
     * @return the targets to deploy.
     */
    @NonNull
    public final List<T> getTargets() {
        return targets;
    }

    /**
     * Checks if this deploy set is valid for use against the specified project by the specified authentication.
     *
     * @param project        the {@link AbstractProject} to check (will be the last successful {@link Run} of the
     *                       project).
     * @param authentication the {@link Authentication} that the deployment would be performed as.
     * @return {@code true} if and only if this deploy set is valid for use against the specified project by the
     *         specified authentication.
     */
    public final boolean isValid(AbstractProject<?, ?> project, Authentication authentication) {
        Run<?, ?> lastSuccessfulBuild = CapabilitiesResolver.of(project).getLastSuccessfulBuild(project);
        return lastSuccessfulBuild != null && isValid(lastSuccessfulBuild, authentication);
    }

    /**
     * Checks if this deploy set is valid for use against the specified project by the system authentication.
     *
     * @param project the {@link AbstractProject} to check (will be the last successful {@link Run} of the
     *                project).
     * @return {@code true} if and only if this deploy set is valid for use against the specified project by the
     *         system authentication.
     */
    @SuppressWarnings("unused") // used by stapler
    public final boolean isValid(AbstractProject<?, ?> project) {
        return isValid(project, ACL.SYSTEM);
    }

    /**
     * Checks if this deploy set is valid for use against the specified run by the system authentication.
     *
     * @param run the {@link Run} to check.
     * @return {@code true} if and only if this deploy set is valid for use against the specified run by the
     *         system authentication.
     */
    @SuppressWarnings("unused") // used by stapler
    public final boolean isValid(Run<?, ?> run) {
        return isValid(run, ACL.SYSTEM);
    }

    /**
     * Checks if this deploy set is valid for use against the specified run by the specified authentication.
     *
     * @param run            the {@link Run} to check.
     * @param authentication the {@link Authentication} that the deployment would be performed as.
     * @return {@code true} if and only if this deploy set is valid for use against the specified run by the
     *         specified authentication.
     */
    public final boolean isValid(Run<?, ?> run, Authentication authentication) {
        if (!isAuthenticationValid(authentication)) {
            return false;
        }
        for (DeployTarget target : targets) {
            if (!target.isValid(run)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Return {@code true} if and only if this {@link DeployHost} requires to resolve some specific
     * {@link com.cloudbees.plugins.credentials.Credentials} from the {@link CredentialsProvider} API and
     * the provided {@link Authentication} can find those {@link com.cloudbees.plugins.credentials.Credentials}
     *
     * @param authentication the authentication.
     * @return {@code true} if and only if the provided {@link Authentication} can resolve all the required
     *         {@link com.cloudbees.plugins.credentials.Credentials} needed to deploy this {@link DeployHost}.
     */
    protected abstract boolean isAuthenticationValid(Authentication authentication);

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DeployHost");
        sb.append("{targets=").append(targets);
        sb.append('}');
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DeployHost deployHost = (DeployHost) o;

        if (!targets.equals(deployHost.targets)) {
            return false;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return targets.hashCode();
    }

    public abstract String getDisplayName();

    @Override
    public DeployHostDescriptor<S, T> getDescriptor() {
        return (DeployHostDescriptor<S, T>) super.getDescriptor();
    }

    public static List<DeployHost<?, ?>> createDefaults(@CheckForNull AbstractProject<?, ?> owner,
                                                        @CheckForNull Set<DeploySourceOrigin> origins) {
        return createDefaults(CapabilitiesResolver.of(owner).getLastSuccessfulBuild(owner), origins);
    }

    @CheckForNull
    public static List<DeployHost<?, ?>> createDefaults(@CheckForNull Run<?, ?> run,
                                                        @CheckForNull Set<DeploySourceOrigin> origins) {
        List<DeployHost<?, ?>> result = new ArrayList<DeployHost<?, ?>>();
        for (DeployHostDescriptor<?, ?> d : DeployHostDescriptor.all()) {
            if (d.isSupported(origins, run)) {
                DeployHost<?, ?> set = d.createDefault(run, origins);
                if (set != null) {
                    result.add(set);
                }
            }
        }
        return result;
    }

    public static List<DeployHost<?, ?>> updateDefaults(@CheckForNull AbstractProject<?, ?> owner,
                                                        @CheckForNull Set<DeploySourceOrigin> origins,
                                                        List<? extends DeployHost<?, ?>> templates) {
        return updateDefaults(CapabilitiesResolver.of(owner).getLastSuccessfulBuild(owner), origins, templates);
    }

    @CheckForNull
    public static List<DeployHost<?, ?>> updateDefaults(@CheckForNull Run<?, ?> run,
                                                        @CheckForNull Set<DeploySourceOrigin> origins,
                                                        List<? extends DeployHost<?, ?>> templates) {
        List<DeployHost<?, ?>> result = new ArrayList<DeployHost<?, ?>>();
        List<DeployHostDescriptor<?, ?>> remaining = new ArrayList<DeployHostDescriptor<?, ?>>(
                DeployHostDescriptor.all());
        for (DeployHost<?, ?> template : templates) {
            if (template == null) {
                continue;
            }
            DeployHostDescriptor<? extends DeployHost<?, ?>, ? extends DeployTarget<?>> d =
                    template.getDescriptor();
            remaining.remove(d);
            if (!d.isSupported(origins, run)) {
                // ignore if not supported
                continue;
            }
            DeployHost<?, ?> set;
            if (template.getTargets().isEmpty()) {
                set = template.updateDefault(run, origins);
            } else {
                set = template;
            }
            if (set != null) {
                result.add(set);
            }
        }
        if (result.isEmpty()) {
            for (DeployHostDescriptor<?, ?> d : remaining) {
                if (d.isSupported(origins, run)) {
                    DeployHost<?, ?> set = d.createDefault(run, origins);
                    if (set != null) {
                        result.add(set);
                    }
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private S updateDefault(Run<?, ?> run, Set<DeploySourceOrigin> origins) {
        return getDescriptor().updateDefault(run, origins, (S) this);
    }
}
