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
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Hudson;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.cloudbees.plugins.deployer.DeployNowColumn.DescriptorImpl.isDeployPossible;

/**
 * An action for deploying the last successful build's artifacts now.
 */
@ExportedBean
public class DeployNowProjectAction implements Action {

    /**
     * The owner of this action.
     */
    private final AbstractProject<?, ?> owner;

    /**
     * {@link DeployNowRunAction#CONFIGURE}
     */
    public static final Permission CONFIGURE = DeployNowRunAction.CONFIGURE;

    /**
     * {@link DeployNowRunAction#DEPLOY}
     */
    public static final Permission DEPLOY = DeployNowRunAction.DEPLOY;

    /**
     * {@link DeployNowRunAction#OWN_AUTH}
     */
    public static final Permission OWN_AUTH = DeployNowRunAction.OWN_AUTH;

    /**
     * {@link DeployNowRunAction#JOB_AUTH}
     */
    public static final Permission JOB_AUTH = DeployNowRunAction.JOB_AUTH;

    /**
     * Constructs the action for a specific project.
     *
     * @param owner the owner
     */
    public DeployNowProjectAction(AbstractProject<?, ?> owner) {
        this.owner = owner;
    }

    /**
     * {@inheritDoc}
     */
    public String getIconFileName() {
        return isDeployPossible(owner) ? Jenkins.RESOURCE_PATH
                + "/plugin/cloudbees-deployer-plugin/images/24x24/deploy-now.png" : null;
    }

    /**
     * {@inheritDoc}
     */
    public String getDisplayName() {
        return Messages.DeployNowProjectAction_DisplayName();
    }

    /**
     * Returns the valid sources for this type of deployment.
     *
     * @return the valid sources for this type of deployment.
     */
    @SuppressWarnings("unused") // used by stapler
    public Set<DeploySourceOrigin> getSources() {
        return Collections.singleton(DeploySourceOrigin.RUN);
    }

    /**
     * {@inheritDoc}
     */
    public String getUrlName() {
        return "deploy-now";
    }

    /**
     * Returns the project that this action is owned by.
     *
     * @return the project that this action is owned by.
     */
    public AbstractProject<?, ?> getOwner() {
        return owner;
    }

    /**
     * Returns the most recent successful or stable build of the project that this action is owned by.
     *
     * @return the most recent successful or stable build of the project that this action is owned by.
     */
    @SuppressWarnings("unused") // by stapler
    @CheckForNull
    public AbstractBuild<?, ?> getLastDeployableBuild() {
        return AbstractBuild.class.cast(CapabilitiesResolver.getLastDeployableBuild(owner));
    }

    /**
     * Called by stapler to create the {@link com.cloudbees.plugins.deployer.hosts.DeployHostsContext}.
     *
     * @return the context.
     * @since 4.0
     */
    @SuppressWarnings("unused") // by stapler
    public DeployHostsContext<DeployNowProjectAction> createHostsContext() {
        List<? extends DeployHost<?, ?>> sets = null;
        if (owner != null) {
            DeployNowJobProperty property = owner.getProperty(DeployNowJobProperty.class);
            if (property != null) {
                sets = property.getHosts();
                sets = DeployHost.updateDefaults(owner, Collections.singleton(DeploySourceOrigin.RUN), sets);
            }
        }
        if (sets == null) {
            sets = DeployHost.createDefaults(owner, Collections.singleton(DeploySourceOrigin.RUN));
        }
        return new DeployHostsContext<DeployNowProjectAction>(this,
                sets,
                owner,
                Collections.singleton(DeploySourceOrigin.RUN),
                false,
                true);
    }

    /**
     * Returns {@code true} if and only if the deployment could be a one click deployment.
     *
     * @return {@code true} if and only if the deployment could be a one click deployment.
     */
    @SuppressWarnings("unused") // used by stapler
    @Exported(name = "oneClickDeployPossible", visibility = 2)
    public boolean isOneClickDeployPossible() {
        return isDeployPossible(owner);
    }

    /**
     * Returns {@code true} if and only if the deployment is a one click deployment.
     *
     * @return {@code true} if and only if the deployment is a one click deployment.
     */
    @SuppressWarnings("unused") // used by stapler
    @Exported(name = "oneClickDeployReady", visibility = 2)
    public boolean isOneClickDeploy() {
        if (owner != null) {
            DeployNowJobProperty property = owner.getProperty(DeployNowJobProperty.class);
            if (property != null) {
                return property.isOneClickDeploy();
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if and only if a one click deployment is valid. In other words
     * {@link #isOneClickDeployPossible()} says there are artifacts for deployment. {@link #isOneClickDeployValid()}
     * says the configured one click deploy is fully defined and {@link #isOneClickDeploy()} says that the user
     * has enabled one click deploy for the project.
     *
     * @return {@code true} if and only if a one click deployment is valid.
     */
    @SuppressWarnings("unused") // used by stapler
    @Exported(name = "oneClickDeployValid", visibility = 2)
    public boolean isOneClickDeployValid() {
        if (owner != null && owner.hasPermission(DEPLOY)) {
            DeployNowJobProperty property = owner.getProperty(DeployNowJobProperty.class);
            if (property != null) {
                if (property.isOneClickDeploy()) {
                    if (owner.hasPermission(OWN_AUTH) && DeployHost.isValid(property.getHosts(), owner,
                            Hudson.getAuthentication())) {
                        return true;
                    }
                    if (owner.hasPermission(JOB_AUTH) && DeployHost.isValid(property.getHosts(), owner,
                            ACL.SYSTEM)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if and only if the configuration must be saved (because we do not have a configuration).
     *
     * @return {@code true} if and only if the configuration must be saved (because we do not have a configuration).
     */
    @SuppressWarnings("unused") // used by stapler
    public boolean isSaveConfigForced() {
        if (owner != null) {
            DeployNowJobProperty property = owner.getProperty(DeployNowJobProperty.class);
            return property == null;
        }
        return false;
    }

    /**
     * Starts the deploy now action.
     *
     * @param req the request.
     * @return the response.
     */
    @SuppressWarnings("unused") // used by stapler
    public HttpResponse doIndex(StaplerRequest req) {
        if (!isDeployPossible(owner)) {
            return HttpResponses.notFound();
        }
        return HttpResponses.forwardToView(this, "configure");
    }

    /**
     * Returns the descriptor of the job property.
     *
     * @return the descriptor of the job property.
     */
    @SuppressWarnings("unused") // used by stapler
    public DeployNowJobProperty.DescriptorImpl getDescriptor() {
        return (DeployNowJobProperty.DescriptorImpl) Hudson.getInstance().getDescriptor(DeployNowJobProperty.class);
    }

}
