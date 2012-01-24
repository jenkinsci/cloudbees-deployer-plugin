/*
 * Copyright 2010-2011, CloudBees Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkins.plugins.cloudbees;

import com.cloudbees.EndPoints;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesUser;
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesUserWithAccountApiKey;
import com.cloudbees.plugins.deployer.DeployPublisher;
import com.cloudbees.plugins.deployer.impl.run.RunHostImpl;
import com.cloudbees.plugins.deployer.impl.run.RunTargetImpl;
import com.cloudbees.plugins.deployer.sources.WildcardPathDeploySource;
import com.cloudbees.plugins.registration.CloudBeesUserImpl;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.CopyOnWriteList;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Olivier Lamy
 * @deprecated
 */
@Deprecated
public class CloudbeesPublisher extends Notifier {

    public final String accountName;

    public final String applicationId;

    public final String filePattern;

    @DataBoundConstructor
    public CloudbeesPublisher(String accountName, String applicationId, String filePattern)
            throws Exception {
        if (accountName == null) {
            // revert to first one
            Iterator<CloudbeesAccount> iterator = DESCRIPTOR.accounts.iterator();

            if (iterator != null && iterator.hasNext()) {
                accountName = iterator.next().name;
            } else {
                accountName = "";
            }
        }
        this.accountName = accountName;

        this.applicationId = applicationId;

        this.filePattern = filePattern;
    }

    @Initializer(after = InitMilestone.PLUGINS_STARTED)
    public static void transitionAuth() throws IOException {
        DescriptorImpl that = (DescriptorImpl) Hudson.getInstance().getDescriptor(CloudbeesPublisher.class);
        if (that == null || that.accounts == null || that.accounts.isEmpty()) {
            return;
        }
        SystemCredentialsProvider provider = SystemCredentialsProvider.getInstance();
        if (provider == null) {
            return;
        }
        boolean addMissingCredentials =
                CredentialsProvider.lookupCredentials(CloudBeesUserWithAccountApiKey.class).isEmpty()
                        && Boolean.parseBoolean(
                        System.getProperty(CloudbeesPublisher.class.getName() + ".addMissingCredentials", "true"));
        List<Credentials> credentials = provider.getCredentials();
        for (CloudbeesAccount account : that.accounts) {
            boolean match = false;
            for (Iterator<Credentials> iterator = credentials.iterator(); iterator.hasNext(); ) {
                Credentials u = iterator.next();
                if (u instanceof CloudBeesUser) {
                    CloudBeesUser cb = (CloudBeesUser) u;
                    if (StringUtils.equals(cb.getAPIKey(), account.apiKey)) {
                        match = true;
                    } else if (StringUtils.equals(cb.getName(), account.name)) {
                        iterator.remove();
                    }
                }
            }
            if (!match && addMissingCredentials) {
                LOGGER.warning("Could not find matching credentials for CloudBees account " + account.name
                        + ", please add corresponding details to the Manage Credentials screen");
                // ugly, and not even correct but best we can do to help hint the user
                CloudBeesUserImpl user = new CloudBeesUserImpl(CredentialsScope.GLOBAL, account.name, null);
                credentials.add(user);
            }
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    /**
     * Called when object has been deserialized from a stream.
     *
     * @return {@code this}, or a replacement for {@code this}.
     * @throws java.io.ObjectStreamException if the object cannot be restored.
     * @see <a href="http://download.oracle.com/javase/1.3/docs/guide/serialization/spec/input.doc6.html">The Java
     *      Object Serialization Specification</a>
     */
    private Object readResolve() throws ObjectStreamException {
        String accountName = Util.fixEmptyAndTrim(this.accountName);
        String applicationId = StringUtils.removeStart(this.applicationId, accountName + "/");
        int index = applicationId.lastIndexOf('/');
        if (index != -1 && index + 1 < applicationId.length()) {
            applicationId = applicationId.substring(index + 1);
        }
        List<RunTargetImpl> deployTargets = new ArrayList<RunTargetImpl>();
        if (StringUtils.isNotEmpty(applicationId)) {
            deployTargets
                    .add(new RunTargetImpl(EndPoints.runAPI(), applicationId, null, null, null,
                            new WildcardPathDeploySource(StringUtils.isEmpty(filePattern) ? "**/*.war" : filePattern), false, null, null, null));
        }
        CloudbeesAccount account = null;
        for (CloudbeesAccount a : DESCRIPTOR.accounts) {
            if (accountName.equals(a.name)) {
                account = a;
                break;
            }
        }
        CloudBeesUser user = null;
        if (account != null) {
            for (CloudBeesUser u : CredentialsProvider.lookupCredentials(CloudBeesUser.class)) {
                if (u.getAPIKey().equals(account.apiKey)) {
                    user = u;
                    break;
                }
            }
        }
        return new DeployPublisher(
                Arrays.asList(new RunHostImpl(user != null ? user.getName() : null, accountName, deployTargets)),
                false);
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, final BuildListener listener)
            throws InterruptedException, IOException {
        // no-op withCredentialsAs we should be replaced
        return true;
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl
            extends BuildStepDescriptor<Publisher> {

        private final CopyOnWriteList<CloudbeesAccount> accounts = new CopyOnWriteList<CloudbeesAccount>();

        // public so could be disable programatically
        public transient boolean disableAccountSetup = false;

        // configurable ?
        // so here last with a public static field it's possible to change tru a groovy script
        public static String CLOUDBEES_API_URL = "https://api.cloudbees.com/api";

        public DescriptorImpl() {
            super(CloudbeesPublisher.class);
            load();
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) {
            save();
            return true;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            // check if type of FreeStyleProject.class or MavenModuleSet.class
            return false;
        }

    }

    private static final Logger LOGGER = Logger.getLogger(CloudbeesPublisher.class.getName());
}
