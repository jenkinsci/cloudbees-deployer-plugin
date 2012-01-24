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

package com.cloudbees.plugins.deployer.impl.run;

import com.cloudbees.EndPoints;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesAccount;
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesUser;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.deployer.DeployNowRunAction;
import com.cloudbees.plugins.deployer.hosts.DeployHost;
import com.cloudbees.plugins.deployer.hosts.DeployHostDescriptor;
import com.cloudbees.plugins.deployer.hosts.Messages;
import com.cloudbees.plugins.deployer.sources.DeploySourceOrigin;
import com.cloudbees.plugins.deployer.sources.MavenArtifactDeploySource;
import com.cloudbees.plugins.deployer.sources.StaticSelectionDeploySource;
import com.cloudbees.plugins.registration.run.CloudBeesClient;
import com.cloudbees.plugins.registration.run.CloudBeesClientFactory;
import com.ning.http.client.AsyncHttpClientConfig;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.reporters.MavenArtifact;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.plugins.asynchttpclient.AHCUtils;
import org.acegisecurity.Authentication;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;

import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * @author stephenc
 * @since 13/12/2012 13:00
 */
public class RunHostImpl extends DeployHost<RunHostImpl, RunTargetImpl> {

    /**
     * The maximum number of targets to infer.
     */
    private static int MAX_AUTO_TARGETS = Integer.getInteger(RunHostImpl.class.getName() + ".MAX_AUTO_TARGETS", 10);

    @CheckForNull
    private final String user;

    @CheckForNull
    private final String account;

    @DataBoundConstructor
    public RunHostImpl(String user, String account, List<RunTargetImpl> targets) {
        super(targets);
        this.user = user;
        this.account = account;
    }

    @CheckForNull
    public String getUser() {
        return user;
    }

    @CheckForNull
    public String getAccount() {
        return account;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isAuthenticationValid(Authentication authentication) {
        CloudBeesUser cloudBeesUser = DescriptorImpl.getCloudBeesUser(user, authentication);
        if (cloudBeesUser == null) {
            return false;
        }
        List<CloudBeesAccount> accounts = cloudBeesUser.getAccounts();
        if (accounts == null) {
            return false;
        }
        CloudBeesAccount cloudBeesAccount = null;
        for (CloudBeesAccount a : accounts) {
            if (a.getName().equals(account)) {
                cloudBeesAccount = a;
                break;
            }
        }
        return cloudBeesAccount != null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("RunHostImpl");
        sb.append("{user='").append(user).append('\'');
        sb.append(", account='").append(account).append('\'');
        sb.append(", super='").append(super.toString()).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public String getDisplayName() {
        final StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (RunTargetImpl target : getTargets()) {
            if (first) {
                sb.append(account);
                sb.append("/(");
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(target.getApplicationId());
        }
        if (!first) {
            sb.append(")");
        }
        return sb.toString();
    }

    /**
     * The {@link com.cloudbees.plugins.deployer.hosts.DeployHostDescriptor} for {@link RunHostImpl}
     */
    @Extension(ordinal = 1.0)
    public static class DescriptorImpl extends DeployHostDescriptor<RunHostImpl, RunTargetImpl> {

        /**
         * A cache of targets that have been built
         */
        private final LinkedHashMap<TargetKey, SoftReference<List<RunTargetImpl>>> newTargetCache = new LinkedHashMap
                <TargetKey, SoftReference<List<RunTargetImpl>>>(25, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(
                    Map.Entry<TargetKey, SoftReference<List<RunTargetImpl>>> eldest) {
                return size() >= 25;
            }
        };

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public Class<RunTargetImpl> getDeployTargetClass() {
            return RunTargetImpl.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.CloudBeesRunSet_DisplayName();
        }

        @SuppressWarnings("unused") // used by stapler
        public ListBoxModel doFillUserItems(@QueryParameter String usersAuth) {
            ListBoxModel m = new ListBoxModel();
            Set<String> names = new HashSet<String>();

            Item item = Stapler.getCurrentRequest().findAncestorObject(Item.class);
            if (!StringUtils.isEmpty(usersAuth) && item.hasPermission(DeployNowRunAction.OWN_AUTH)) {
                for (CloudBeesUser u : CredentialsProvider.lookupCredentials(CloudBeesUser.class, item,
                        Hudson.getAuthentication(), Collections.<DomainRequirement>emptyList())) {
                    m.add(u.getDisplayName(), u.getName());
                    names.add(u.getName());
                }
            }

            if (item.hasPermission(DeployNowRunAction.JOB_AUTH)) {
                for (CloudBeesUser u : CredentialsProvider.lookupCredentials(CloudBeesUser.class, item, ACL.SYSTEM)) {
                    if (!names.contains(u.getName())) {
                        m.add(u.getDisplayName(), u.getName());
                        names.add(u.getName());
                    }
                }
            }

            return m;
        }

        @SuppressWarnings("unused") // used by stapler
        public FormValidation doCheckUser(@QueryParameter String usersAuth,
                                          @QueryParameter final String value)
                throws IOException, ServletException {
            if (StringUtils.isBlank(value)) {
                return FormValidation.warning("The user to deploy as must be specified");
            }

            Item item = Stapler.getCurrentRequest().findAncestorObject(Item.class);

            CloudBeesUser cloudBeesUser = null;

            if (!StringUtils.isEmpty(usersAuth) && item.hasPermission(DeployNowRunAction.OWN_AUTH)) {
                cloudBeesUser = getCloudBeesUser(value, Hudson.getAuthentication());
            }

            if (cloudBeesUser == null && item.hasPermission(DeployNowRunAction.JOB_AUTH)) {
                cloudBeesUser = getCloudBeesUser(value, ACL.SYSTEM);
            }

            if (cloudBeesUser == null) {
                return FormValidation
                        .error("The specified user does not exist / you do not have permission to access the "
                                + "specified user's credentials");
            }
            return FormValidation.ok();
        }

        @SuppressWarnings("unused") // used by stapler
        public ListBoxModel doFillAccountItems(@QueryParameter String usersAuth, @QueryParameter String user) {
            ListBoxModel m = new ListBoxModel();

            user = Util.fixEmptyAndTrim(user);
            if (user == null) {
                return m;
            }

            Item item = Stapler.getCurrentRequest().findAncestorObject(Item.class);

            CloudBeesUser u = null;

            if (!StringUtils.isEmpty(usersAuth) && item.hasPermission(DeployNowRunAction.OWN_AUTH)) {
                u = getCloudBeesUser(user, Hudson.getAuthentication());
            }

            if (u == null && item.hasPermission(DeployNowRunAction.JOB_AUTH)) {
                u = getCloudBeesUser(user, ACL.SYSTEM);
            }

            if (u == null) {
                return m;
            }

            List<CloudBeesAccount> accounts = u.getAccounts();
            if (accounts != null) {
                for (CloudBeesAccount a : accounts) {
                    m.add(a.getName(), a.getName());
                }
            }

            return m;
        }

        @SuppressWarnings("unused") // used by stapler
        public FormValidation doCheckAccount(@QueryParameter String usersAuth,
                                             @QueryParameter final String user,
                                             @QueryParameter final String value)
                throws IOException, ServletException {
            if (StringUtils.isBlank(user)) {
                return FormValidation.ok();  // somebody else will flag this issue
            }

            if (StringUtils.isBlank(value)) {
                return FormValidation.warning("The account to deploy into must be specified");
            }

            Item item = Stapler.getCurrentRequest().findAncestorObject(Item.class);

            CloudBeesUser cloudBeesUser = null;

            if (!StringUtils.isEmpty(usersAuth) && item.hasPermission(DeployNowRunAction.OWN_AUTH)) {
                cloudBeesUser = getCloudBeesUser(user, Hudson.getAuthentication());
            }

            if (cloudBeesUser == null && item.hasPermission(DeployNowRunAction.JOB_AUTH)) {
                cloudBeesUser = getCloudBeesUser(user, ACL.SYSTEM);
            }

            if (cloudBeesUser == null) {
                return FormValidation.ok();  // somebody else will flag this issue
            }
            CloudBeesAccount cloudBeesAccount = cloudBeesUser.getAccount(value);
            if (cloudBeesAccount == null) {
                return FormValidation
                        .error("The specified account does not exist / the selected user does not have access to the "
                                + "specified account");
            }
            return FormValidation.ok();
        }

        private static CloudBeesUser getCloudBeesUser(String user, Authentication authentication) {
            for (CloudBeesUser u : CredentialsProvider.lookupCredentials(CloudBeesUser.class,
                    Stapler.getCurrentRequest().findAncestorObject(Item.class), authentication)) {
                if (u.getName().equals(user)) {
                    return u;
                }
            }

            return null;
        }

        @SuppressWarnings("unused") // used by stapler
        public FormValidation doCheckApplicationId(@QueryParameter String usersAuth,
                                                   @QueryParameter final String value,
                                                   @QueryParameter final String user,
                                                   @QueryParameter final String account)
                throws IOException, ServletException {
            try {

                if (StringUtils.isBlank(user) || StringUtils.isBlank(account)) {
                    return FormValidation.ok();  // somebody else will flag this issue
                }

                if (StringUtils.isBlank(value)) {
                    return FormValidation.error("Application Id cannot be empty");
                }

                Item item = Stapler.getCurrentRequest().findAncestorObject(Item.class);

                CloudBeesUser cloudBeesUser = null;

                if (!StringUtils.isEmpty(usersAuth) && item.hasPermission(DeployNowRunAction.OWN_AUTH)) {
                    cloudBeesUser = getCloudBeesUser(user, Hudson.getAuthentication());
                }

                if (cloudBeesUser == null && item.hasPermission(DeployNowRunAction.JOB_AUTH)) {
                    cloudBeesUser = getCloudBeesUser(user, ACL.SYSTEM);
                }

                if (cloudBeesUser == null) {
                    return FormValidation.ok();  // somebody else will flag this issue
                }
                CloudBeesAccount cloudBeesAccount = cloudBeesUser.getAccount(account);
                if (cloudBeesAccount == null) {
                    return FormValidation.ok(); // somebody else will flag this issue
                }

                CloudBeesClient client = new CloudBeesClientFactory()
                        .withClientConfig(
                                new AsyncHttpClientConfig.Builder().setRequestTimeoutInMs(25000)
                                        .build())
                        .withProxyServer(AHCUtils.getProxyServer())
                        .withAuthentication(cloudBeesUser.getAPIKey(), cloudBeesUser.getAPISecret().getPlainText())
                        .build();
                try {
                    String matchName = account + "/" + value;
                    for (String name : client
                            .getApplicationsStatuses(cloudBeesAccount.getName())
                            .get(30, TimeUnit.SECONDS).keySet()) {
                        if (name.equals(matchName)) {
                            return FormValidation.ok();
                        }
                    }
                } finally {
                    client.close();
                }
                return FormValidation
                        .warning("This application ID was not found, so using it will create a new application");
            } catch (Exception e) {
                return FormValidation.error(e, "error during check applicationId " + e.getMessage());
            }
        }

        @SuppressWarnings("unused") // used by stapler
        public ComboBoxModel doFillApplicationIdItems(@QueryParameter final String usersAuth,
                                                      @QueryParameter final String user,
                                                      @QueryParameter final String account) {
            try {

                if (StringUtils.isBlank(user) || StringUtils.isBlank(account)) {
                    return new ComboBoxModel();
                }

                Item item = Stapler.getCurrentRequest().findAncestorObject(Item.class);

                CloudBeesUser cloudBeesUser = null;

                if (!StringUtils.isEmpty(usersAuth) && item.hasPermission(DeployNowRunAction.OWN_AUTH)) {
                    cloudBeesUser = getCloudBeesUser(user, Hudson.getAuthentication());
                }

                if (cloudBeesUser == null && item.hasPermission(DeployNowRunAction.JOB_AUTH)) {
                    cloudBeesUser = getCloudBeesUser(user, ACL.SYSTEM);
                }

                if (cloudBeesUser == null) {
                    return new ComboBoxModel();
                }

                CloudBeesAccount cloudBeesAccount = cloudBeesUser.getAccount(account);

                if (cloudBeesAccount == null) {
                    return new ComboBoxModel();
                }

                Set<String> names = new TreeSet<String>();
                CloudBeesClient client = new CloudBeesClientFactory()
                        .withClientConfig(
                                new AsyncHttpClientConfig.Builder().setRequestTimeoutInMs(25000)
                                        .build())
                        .withProxyServer(AHCUtils.getProxyServer())
                        .withAuthentication(cloudBeesUser.getAPIKey(), cloudBeesUser.getAPISecret().getPlainText())
                        .build();
                try {
                    String prefix = account + "/";
                    for (String name : client
                            .getApplicationsStatuses(cloudBeesAccount.getName())
                            .get(30, TimeUnit.SECONDS).keySet()) {
                        if (name.startsWith(prefix)) {
                            names.add(name.substring(prefix.length()));
                        }
                    }
                } finally {
                    client.close();
                }
                return new ComboBoxModel(names);
            } catch (Exception e) {
                return new ComboBoxModel();
            }

        }

        /**
         * {@inheritDoc}
         */
        @Override
        @CheckForNull
        public RunHostImpl createDefault(@CheckForNull Run<?, ?> run,
                                         @CheckForNull Set<DeploySourceOrigin> origins) {
            return isSupported(origins, run) ? new RunHostImpl(null, null, createTargets(run, origins)) : null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @CheckForNull
        public RunHostImpl updateDefault(@CheckForNull Run<?, ?> run, @CheckForNull Set<DeploySourceOrigin> origins,
                                         RunHostImpl template) {
            return isSupported(origins, run)
                    ? new RunHostImpl(template.getUser(), template.getAccount(), createTargets(run, origins))
                    : null;
        }

        private List<RunTargetImpl> createTargets(Run<?, ?> run, Set<DeploySourceOrigin> origins) {
            TargetKey key;
            if (run != null) {
                key = new TargetKey(run, origins);
                final SoftReference<List<RunTargetImpl>> reference = newTargetCache.get(key);
                if (reference != null) {
                    final List<RunTargetImpl> targets = reference.get();
                    if (targets != null) {
                        return targets;
                    }
                }
            } else {
                key = null;
            }
            List<RunTargetImpl> result = new ArrayList<RunTargetImpl>();
            if (origins.contains(DeploySourceOrigin.RUN)) {
                if (run instanceof MavenModuleSetBuild) {
                    final MavenModuleSetBuild builds = (MavenModuleSetBuild) run;
                    for (List<MavenBuild> mavenBuilds : builds.getModuleBuilds().values()) {
                        if (result.size() > MAX_AUTO_TARGETS) {
                            break;
                        }
                        for (MavenBuild build : mavenBuilds) {
                            if (result.size() > MAX_AUTO_TARGETS) {
                                break;
                            }
                            List<MavenArtifactRecord> records = build.getActions(MavenArtifactRecord.class);
                            if (records != null) {
                                for (MavenArtifactRecord record : records) {
                                    if (result.size() > MAX_AUTO_TARGETS) {
                                        break;
                                    }
                                    MavenArtifact mainArtifact = record.mainArtifact;
                                    if ("war".equals(mainArtifact.type)) {
                                        result.add(new RunTargetImpl(EndPoints.runAPI(), null, null, null, null,
                                                new MavenArtifactDeploySource(mainArtifact.groupId,
                                                                                                mainArtifact.artifactId,
                                                                                                mainArtifact.classifier, mainArtifact.type), false, null, null, null));
                                    }
                                    for (MavenArtifact artifact : record.attachedArtifacts) {
                                        if (result.size() > MAX_AUTO_TARGETS) {
                                            break;
                                        }
                                        if ("war".equals(artifact.type)) {
                                            result.add(new RunTargetImpl(EndPoints.runAPI(), null, null, null, null,
                                                    new MavenArtifactDeploySource(mainArtifact.groupId,
                                                                                                        mainArtifact.artifactId,
                                                                                                        mainArtifact.classifier, mainArtifact.type), false, null, null, null));
                                        }
                                    }
                                }
                            }
                        }

                    }
                } else if (run instanceof MavenBuild) {
                    final MavenBuild build = (MavenBuild) run;
                    List<MavenArtifactRecord> records = build.getActions(MavenArtifactRecord.class);
                    if (records != null) {
                        for (MavenArtifactRecord record : records) {
                            if (result.size() > MAX_AUTO_TARGETS) {
                                break;
                            }
                            MavenArtifact mainArtifact = record.mainArtifact;
                            if ("war".equals(mainArtifact.type)) {
                                result.add(new RunTargetImpl(EndPoints.runAPI(), null, null, null, null,
                                        new MavenArtifactDeploySource(mainArtifact.groupId,
                                                                                mainArtifact.artifactId,
                                                                                mainArtifact.classifier, mainArtifact.type), false, null, null, null));
                            }
                            for (MavenArtifact artifact : record.attachedArtifacts) {
                                if (result.size() > MAX_AUTO_TARGETS) {
                                    break;
                                }
                                if ("war".equals(artifact.type)) {
                                    result.add(new RunTargetImpl(EndPoints.runAPI(), null, null, null, null,
                                            new MavenArtifactDeploySource(mainArtifact.groupId,
                                                                                        mainArtifact.artifactId,
                                                                                        mainArtifact.classifier, mainArtifact.type), false, null, null, null));
                                }
                            }
                        }
                    }
                } else {
                    if (run != null && run.getArtifactsDir().isDirectory() && run.getHasArtifacts()) {
                        FileSet fileSet = new FileSet();
                        fileSet.setProject(new Project());
                        fileSet.setDir(run.getArtifactsDir());
                        fileSet.setIncludes("**/*.war");
                        for (String path : fileSet.getDirectoryScanner().getIncludedFiles()) {
                            if (result.size() > MAX_AUTO_TARGETS) {
                                break;
                            }
                            result.add(new RunTargetImpl(EndPoints.runAPI(), null, null, null, null,
                                    new StaticSelectionDeploySource(path), false, null, null, null));
                        }
                    }
                }
            }
            if (key != null) {
                newTargetCache.put(key, new SoftReference<List<RunTargetImpl>>(result));
            }
            return result;
        }

    }

    /**
     * A simple holder for the caching of calls to {@link DescriptorImpl#createTargets(hudson.model.Run,
     * java.util.Set)}.
     */
    private static final class TargetKey {
        /**
         * The run.
         */
        @NonNull
        private final Run<?, ?> run;

        /**
         * The list of origins.
         */
        @CheckForNull
        private Set<DeploySourceOrigin> origins;

        /**
         * Creates an instance.
         *
         * @param run     the run.
         * @param origins the list of origins.
         */
        private TargetKey(@NonNull Run<?, ?> run, @CheckForNull Set<DeploySourceOrigin> origins) {
            run.getClass(); // throw NPE if null
            this.run = run;
            this.origins = origins;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TargetKey)) {
                return false;
            }

            TargetKey targetKey = (TargetKey) o;

            if (origins != null ? !origins.equals(targetKey.origins) : targetKey.origins != null) {
                return false;
            }
            if (!run.equals(targetKey.run)) {
                return false;
            }

            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            int result = run.hashCode();
            result = 31 * result + (origins != null ? origins.hashCode() : 0);
            return result;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("TargetKey");
            sb.append("{run=").append(run);
            sb.append(", origins=").append(origins);
            sb.append('}');
            return sb.toString();
        }
    }
}
