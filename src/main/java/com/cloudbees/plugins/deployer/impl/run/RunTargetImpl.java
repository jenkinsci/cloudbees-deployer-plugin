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
import com.cloudbees.api.BeesClient;
import com.cloudbees.api.BeesClientConfiguration;
import com.cloudbees.api.ServiceResourceInfo;
import com.cloudbees.api.ServiceResourceListResponse;
import com.cloudbees.api.ServiceSubscriptionInfo;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesAccount;
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesUser;
import com.cloudbees.plugins.deployer.DeployNowRunAction;
import com.cloudbees.plugins.deployer.NamedThreadFactory;
import com.cloudbees.plugins.deployer.sources.DeploySource;
import com.cloudbees.plugins.deployer.targets.DeployTarget;
import com.cloudbees.plugins.deployer.targets.DeployTargetDescriptor;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ProxyConfiguration;
import hudson.RelativePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.ComboBoxModel;
import hudson.util.ExceptionCatchingThreadFactory;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.TimeUnit2;
import org.acegisecurity.Authentication;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author stephenc
 * @since 12/12/2012 15:00
 */
public class RunTargetImpl extends DeployTarget<RunTargetImpl> {
    private static final Logger LOGGER = Logger.getLogger(RunTargetImpl.class.getName());
    /**
     * The application id.
     */
    private final String applicationId;

    /**
     * The application environment.
     */
    private final String applicationEnvironment;

    /**
     * The application configuration.
     */
    @CheckForNull
    private final Setting[] applicationConfig;

    /**
     * The RUN API end-point for the server holding the application
     */
    private final String apiEndPoint;

    /**
     * The description to use for the deployment.
     */
    private final String deploymentDescription;

    private final boolean deltaDeployment;

    private final String clickStackName;

    @CheckForNull
    private final Setting[] clickStackConfig;

    @CheckForNull
    private final Setting[] clickStackRuntimeConfig;

    /**
     * @deprecated Retained for backwards API compatibility
     */
    @Deprecated
    public RunTargetImpl(String apiEndPoint, String applicationId, String applicationEnvironment,
                         String deploymentDescription, Setting[] applicationConfig, DeploySource artifact) {
        this(apiEndPoint, applicationId, applicationEnvironment, deploymentDescription, applicationConfig,
                artifact, false, null, null, null);
    }

    /**
     * @since 4.14
     */
    @DataBoundConstructor
    public RunTargetImpl(String apiEndPoint, String applicationId, String applicationEnvironment,
                         String deploymentDescription, Setting[] applicationConfig, DeploySource artifact,
                         boolean deltaDeployment, String clickStackName, Setting[] clickStackConfig,
                         Setting[] clickStackRuntimeConfig) {
        super(artifact);
        this.apiEndPoint = StringUtils.isBlank(apiEndPoint) ? EndPoints.runAPI() : apiEndPoint;
        this.applicationConfig = applicationConfig == null ? new Setting[0] : applicationConfig.clone();
        this.applicationEnvironment = Util.fixEmptyAndTrim(applicationEnvironment);
        this.applicationId = Util.fixEmptyAndTrim(applicationId);
        this.deploymentDescription = Util.fixEmptyAndTrim(deploymentDescription);
        this.deltaDeployment = deltaDeployment;
        this.clickStackName = Util.fixEmptyAndTrim(clickStackName);
        this.clickStackConfig = clickStackConfig == null ? new Setting[0] : clickStackConfig.clone();
        this.clickStackRuntimeConfig =
                clickStackRuntimeConfig == null ? new Setting[0] : clickStackRuntimeConfig.clone();
    }

    public String getApiEndPoint() {
        return apiEndPoint;
    }

    public Setting[] getApplicationConfig() {
        return applicationConfig;
    }

    public String getApplicationEnvironment() {
        return applicationEnvironment;
    }

    /**
     * Gets the application environment that the application should be deployed as for a specific context.
     *
     * @param context  the context.
     * @param listener the listener.
     * @return the application environment to deploy the application as never {@code null}.
     * @throws MacroEvaluationException if macros could not be evaluated.
     * @throws IOException              if an IO exception occured.
     * @throws InterruptedException     if interrupted.
     */
    public String getApplicationEnvironment(AbstractBuild<?, ?> context, TaskListener listener)
            throws MacroEvaluationException, IOException, InterruptedException {
        if (StringUtils.isEmpty(getApplicationEnvironment())) {
            return "run";
        } else {
            String result = expandAllMacros(context, listener, getApplicationEnvironment());
            return StringUtils.isEmpty(result) ? "run" : result;
        }
    }

    @NonNull
    public String getDeploymentDescription() {
        return deploymentDescription == null ? "${JOB_NAME} #${BUILD_NUMBER}" : deploymentDescription;
    }

    /**
     * Gets the description that the application should be deployed with for a specific context.
     *
     * @param context  the context.
     * @param listener the listener.
     * @return the description to deploy the application with never {@code null}.
     * @throws MacroEvaluationException if macros could not be evaluated.
     * @throws IOException              if an IO exception occured.
     * @throws InterruptedException     if interrupted.
     */
    public String getDeploymentDescription(AbstractBuild<?, ?> context, TaskListener listener)
            throws MacroEvaluationException, IOException, InterruptedException {
        if (StringUtils.isEmpty(getDeploymentDescription())) {
            return context.getFullDisplayName();
        } else {
            return expandAllMacros(context, listener, getDeploymentDescription());
        }
    }

    public Setting[] getClickStackConfig() {
        return clickStackConfig;
    }

    public String getClickStackName() {
        return clickStackName;
    }

    public Setting[] getClickStackRuntimeConfig() {
        return clickStackRuntimeConfig;
    }

    public boolean isDeltaDeployment() {
        return deltaDeployment;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public Map<String, String> getApplicationConfigMap() {
        Map<String, String> result = new HashMap<String, String>();
        if (applicationConfig != null) {
            for (Setting s : applicationConfig) {
                final String key = Util.fixNull(s.getKey());
                final String value = Util.fixNull(s.getValue());
                if (StringUtils.isNotEmpty(key)) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    public Map<String, String> getApplicationConfigMap(AbstractBuild<?, ?> context, TaskListener listener)
            throws MacroEvaluationException, IOException, InterruptedException {
        Map<String, String> result = new HashMap<String, String>();
        if (applicationConfig != null) {
            for (Setting s : applicationConfig) {
                final String key = expandAllMacros(context, listener, Util.fixNull(s.getKey()));
                final String value = expandAllMacros(context, listener, Util.fixNull(s.getValue()));
                if (StringUtils.isNotEmpty(key)) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    public Map<String, String> getClickStackConfigMap() {
        Map<String, String> result = new HashMap<String, String>();
        if (clickStackConfig != null) {
            for (Setting s : clickStackConfig) {
                final String key = Util.fixNull(s.getKey());
                final String value = Util.fixNull(s.getValue());
                if (StringUtils.isNotEmpty(key)) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    public Map<String, String> getClickStackConfigMap(AbstractBuild<?, ?> context, TaskListener listener)
            throws MacroEvaluationException, IOException, InterruptedException {
        Map<String, String> result = new HashMap<String, String>();
        if (clickStackConfig != null) {
            for (Setting s : clickStackConfig) {
                final String key = expandAllMacros(context, listener, Util.fixNull(s.getKey()));
                final String value = expandAllMacros(context, listener, Util.fixNull(s.getValue()));
                if (StringUtils.isNotEmpty(key)) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    public Map<String, String> getClickstackConfigMap() {
        Map<String, String> result = new HashMap<String, String>();
        if (clickStackRuntimeConfig != null) {
            for (Setting s : clickStackRuntimeConfig) {
                final String key = Util.fixNull(s.getKey());
                final String value = Util.fixNull(s.getValue());
                if (StringUtils.isNotEmpty(key)) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    public Map<String, String> getClickStackRuntimeConfigMap(AbstractBuild<?, ?> context, TaskListener listener)
            throws MacroEvaluationException, IOException, InterruptedException {
        Map<String, String> result = new HashMap<String, String>();
        if (clickStackRuntimeConfig != null) {
            for (Setting s : clickStackRuntimeConfig) {
                final String key = expandAllMacros(context, listener, Util.fixNull(s.getKey()));
                final String value = expandAllMacros(context, listener, Util.fixNull(s.getValue()));
                if (StringUtils.isNotEmpty(key)) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    public String getApplicationId(AbstractBuild<?, ?> context, BuildListener listener)
            throws MacroEvaluationException, IOException, InterruptedException {
        return expandAllMacros(context, listener, applicationId);
    }

    public String getClickStackName(AbstractBuild<?, ?> context, BuildListener listener)
            throws MacroEvaluationException, IOException, InterruptedException {
        return expandAllMacros(context, listener, clickStackName);
    }

    @Override
    public String getDisplayName() {
        return getApplicationId();
    }

    @Override
    protected boolean isArtifactFileValid(File file) {
        return file.isFile();
    }

    @Override
    protected boolean isComplete() {
        return StringUtils.isNotBlank(applicationId);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RunTargetImpl{");
        sb.append("artifact=").append(getArtifact());
        sb.append(", apiEndPoint='").append(apiEndPoint).append('\'');
        sb.append(", applicationId='").append(applicationId).append('\'');
        sb.append(", applicationEnvironment='").append(applicationEnvironment).append('\'');
        sb.append(", applicationConfig=").append(Arrays.toString(applicationConfig));
        sb.append(", deploymentDescription='").append(deploymentDescription).append('\'');
        sb.append(", deltaDeployment=").append(deltaDeployment);
        sb.append(", clickStackName='").append(clickStackName).append('\'');
        sb.append(", clickStackConfig=").append(Arrays.toString(clickStackConfig));
        sb.append(", clickStackRuntimeConfig=").append(Arrays.toString(clickStackRuntimeConfig));
        sb.append('}');
        return sb.toString();
    }

    @Extension
    public static class DescriptorImpl extends DeployTargetDescriptor<RunTargetImpl> {

        public final ExecutorService executorService =
                new ThreadPoolExecutor(0, 2, 5L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
                        new NamedThreadFactory("RunTargetImpl:UI-Queries",
                                new ExceptionCatchingThreadFactory(
                                        Executors.defaultThreadFactory())));

        private static class CachedMap<K, V> {
            private final long expires;
            private final Future<Map<K, V>> statuses;

            private CachedMap(Future<Map<K, V>> statuses) {
                this.expires = System.currentTimeMillis() + TimeUnit2.MINUTES.toMillis(1);
                this.statuses = statuses;
            }

            public boolean isExpired() {
                return expires < System.currentTimeMillis();
            }

            public Future<Map<K, V>> getStatuses() {
                return statuses;
            }
        }

        private final Map<String, CachedMap<String, String>> applicationStatusCache =
                new LinkedHashMap<String, CachedMap<String, String>>();

        private final Map<String, CachedMap<String, String>> accountEndpointsCache =
                new LinkedHashMap<String, CachedMap<String, String>>();

        @Override
        public String getDisplayName() {
            return Messages.CloudBeesRunTarget_DisplayName();
        }

        @NonNull
        private Map<String, String> getApplicationStatuses(CloudBeesUser cloudBeesUser,
                                                           CloudBeesAccount cloudBeesAccount)
                throws IOException, InterruptedException, ExecutionException, TimeoutException {
            final String cacheKey = cloudBeesUser.getName() + ":" + cloudBeesAccount.getName();
            Map<String, String> applicationStatuses;
            CachedMap<String, String> cacheValue;
            synchronized (applicationStatusCache) {
                cacheValue = applicationStatusCache.get(cacheKey);
                if (cacheValue != null && cacheValue.isExpired()) {
                    applicationStatusCache.remove(cacheKey);
                    cacheValue = null;
                }
            }
            if (cacheValue != null) {
                applicationStatuses = cacheValue.getStatuses().get(30, TimeUnit.SECONDS);
                if (applicationStatuses != null) {
                    return applicationStatuses;
                }
                return Collections.emptyMap();
            }
            BeesClientConfiguration config =
                    new BeesClientConfiguration(EndPoints.runAPI(), cloudBeesUser.getAPIKey(),
                            cloudBeesUser.getAPISecret().getPlainText(), "xml", "1.0");
            if (Hudson.getInstance() != null && Hudson.getInstance().proxy != null) {
                final ProxyConfiguration proxy = Hudson.getInstance().proxy;
                config.setProxyHost(proxy.name);
                config.setProxyPort(proxy.port);
                config.setProxyUser(proxy.getUserName());
                config.setProxyPassword(proxy.getPassword());
            }
            final BeesClient client = new BeesClient(config);
            cacheValue = new CachedMap<String, String>(executorService.submit(
                    new AccountRegionsCallable(client, cloudBeesAccount)));
            synchronized (applicationStatusCache) {
                applicationStatusCache.put(cacheKey, cacheValue);
            }
            applicationStatuses = cacheValue.getStatuses().get(30, TimeUnit.SECONDS);
            if (applicationStatuses != null) {
                return applicationStatuses;
            }
            return Collections.emptyMap();
        }

        private Map<String, String> getAccountEndpoints(CloudBeesUser cloudBeesUser,
                                                        CloudBeesAccount cloudBeesAccount)
                throws IOException, InterruptedException, ExecutionException, TimeoutException {
            final String cacheKey = cloudBeesUser.getName() + ":" + cloudBeesAccount.getName();
            Map<String, String> accountEndpoints;
            CachedMap<String, String> cacheValue;
            synchronized (accountEndpointsCache) {
                cacheValue = accountEndpointsCache.get(cacheKey);
                if (cacheValue != null && cacheValue.isExpired()) {
                    accountEndpointsCache.remove(cacheKey);
                    cacheValue = null;
                }
            }
            if (cacheValue != null) {
                accountEndpoints = cacheValue.getStatuses().get(30, TimeUnit.SECONDS);
                if (accountEndpoints != null) {
                    return accountEndpoints;
                }
                return Collections.emptyMap();
            }
            BeesClientConfiguration config =
                    new BeesClientConfiguration(EndPoints.runAPI(), cloudBeesUser.getAPIKey(),
                            cloudBeesUser.getAPISecret().getPlainText(), "xml", "1.0");
            if (Hudson.getInstance() != null && Hudson.getInstance().proxy != null) {
                final ProxyConfiguration proxy = Hudson.getInstance().proxy;
                config.setProxyHost(proxy.name);
                config.setProxyPort(proxy.port);
                config.setProxyUser(proxy.getUserName());
                config.setProxyPassword(proxy.getPassword());
            }
            final BeesClient client = new BeesClient(config);
            cacheValue = new CachedMap<String, String>(executorService.submit(
                    new AccountEndpointsCallable(client, cloudBeesAccount)));
            synchronized (accountEndpointsCache) {
                accountEndpointsCache.put(cacheKey, cacheValue);
            }
            accountEndpoints = cacheValue.getStatuses().get(30, TimeUnit.SECONDS);
            if (accountEndpoints != null) {
                return accountEndpoints;
            }
            return Collections.emptyMap();
        }

        @SuppressWarnings("unused") // used by stapler
        public FormValidation doCheckApplicationId(@QueryParameter @RelativePath("..") String usersAuth,
                                                   @QueryParameter final String value,
                                                   @QueryParameter @RelativePath("..") final String user,
                                                   @QueryParameter @RelativePath("..") final String account)
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

                final Map<String, String> statuses = getApplicationStatuses(cloudBeesUser, cloudBeesAccount);
                if (statuses.containsKey(value) || statuses.containsKey(account + "/" + value)) {
                    return FormValidation.ok();
                }
                return FormValidation
                        .warning("This application ID was not found, so using it will create a new application");
            } catch (Exception e) {
                return FormValidation.error(e, "Error during check of Application Id: " + e.getMessage());
            }
        }

        @SuppressWarnings("unused") // used by stapler
        public ComboBoxModel doFillApplicationIdItems(@QueryParameter @RelativePath("..") final String usersAuth,
                                                      @QueryParameter @RelativePath("..") final String user,
                                                      @QueryParameter @RelativePath("..") final String account) {
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
                for (String name : getApplicationStatuses(cloudBeesUser, cloudBeesAccount).keySet()) {
                    names.add(name);
                }
                return new ComboBoxModel(names);
            } catch (Exception e) {
                return new ComboBoxModel();
            }

        }

        public ListBoxModel doFillApiEndPointItems(@QueryParameter @RelativePath("..") final String usersAuth,
                                                   @QueryParameter @RelativePath("..") final String user,
                                                   @QueryParameter @RelativePath("..") final String account,
                                                   @QueryParameter final String applicationId) {
            Map<String, String> values = new LinkedHashMap<String, String>();
            String appIdRegion = null;
            values.put(EndPoints.runAPI(), "US");
            try {
                if (!StringUtils.isBlank(user) && !StringUtils.isBlank(account)) {

                    Item item = Stapler.getCurrentRequest().findAncestorObject(Item.class);

                    CloudBeesUser cloudBeesUser = null;

                    if (!StringUtils.isEmpty(usersAuth) && item.hasPermission(DeployNowRunAction.OWN_AUTH)) {
                        cloudBeesUser = getCloudBeesUser(user, Hudson.getAuthentication());
                    }

                    if (cloudBeesUser == null && item.hasPermission(DeployNowRunAction.JOB_AUTH)) {
                        cloudBeesUser = getCloudBeesUser(user, ACL.SYSTEM);
                    }

                    if (cloudBeesUser != null) {

                        CloudBeesAccount cloudBeesAccount = cloudBeesUser.getAccount(account);

                        if (cloudBeesAccount != null) {

                            BeesClientConfiguration config =
                                    new BeesClientConfiguration(EndPoints.runAPI(), cloudBeesUser.getAPIKey(),
                                            cloudBeesUser.getAPISecret().getPlainText(), "xml", "1.0");
                            if (Hudson.getInstance() != null && Hudson.getInstance().proxy != null) {
                                final ProxyConfiguration proxy = Hudson.getInstance().proxy;
                                config.setProxyHost(proxy.name);
                                config.setProxyPort(proxy.port);
                                config.setProxyUser(proxy.getUserName());
                                config.setProxyPassword(proxy.getPassword());
                            }
                            BeesClient client = new BeesClient(config);
                            if (!StringUtils.isBlank(applicationId)) {
                                try {
                                    appIdRegion =
                                            getApplicationStatuses(cloudBeesUser, cloudBeesAccount).get(applicationId);
                                } catch (Exception e) {
                                    // ignore
                                }
                            }
                            values.putAll(getAccountEndpoints(cloudBeesUser, cloudBeesAccount));
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.INFO, "Could not populate ListBoxModel", e);
            }
            ListBoxModel result = new ListBoxModel();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                result.add(new ListBoxModel.Option(entry.getValue(), entry.getKey(),
                        StringUtils.equalsIgnoreCase(entry.getValue(), appIdRegion)));
            }
            return result;

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

        public FormValidation doCheckApplicationParameterName(@QueryParameter String value) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.error("Must not be empty");
            }
            return FormValidation.ok();
        }

        private static class AccountRegionsCallable implements Callable<Map<String, String>> {

            private final String account;
            private final BeesClient client;
            private final CloudBeesAccount cloudBeesAccount;

            public AccountRegionsCallable(BeesClient client, CloudBeesAccount cloudBeesAccount) {
                this.client = client;
                this.cloudBeesAccount = cloudBeesAccount;
                account = cloudBeesAccount.getName();
            }

            public Map<String, String> call() throws Exception {
                try {
                    ServiceResourceListResponse response = client.serviceResourceList("cb-app", account, "application");


                    Map<String, String> accountRegions = new TreeMap<String, String>();
                    String prefix = account + "/";
                    for (ServiceResourceInfo resourceInfo : response.getResources()) {
                        String id = resourceInfo.getId();
                        if (id.startsWith(prefix)) {
                            String region =
                                    resourceInfo.getConfig() != null ? resourceInfo.getConfig().get("region") : null;
                            accountRegions
                                    .put(id.substring(prefix.length()), region == null ? "US" : region.toUpperCase());
                        }
                    }
                    return accountRegions;
                } catch (Exception e) {
                    LOGGER.log(Level.INFO, "Could not get list of applications: ", e);

                }

                return Collections.emptyMap();
            }
        }

        private static class AccountEndpointsCallable implements Callable<Map<String, String>> {

            private final BeesClient client;
            private final CloudBeesAccount cloudBeesAccount;

            public AccountEndpointsCallable(BeesClient client, CloudBeesAccount cloudBeesAccount) {
                this.client = client;
                this.cloudBeesAccount = cloudBeesAccount;
            }

            public Map<String, String> call() throws Exception {
                Map<String, String> result = new LinkedHashMap<String, String>();
                result.put(EndPoints.runAPI(), "US");
                Pattern dcPattern = Pattern.compile("\\Qdc.\\E([^.]+)");
                try {
                    ServiceSubscriptionInfo subscriptionInfo = client.serviceSubscriptionInfo("cb-app",
                            cloudBeesAccount.getName());

                    for (Map.Entry<String, String> entry : subscriptionInfo.getSettings().entrySet()) {
                        Matcher matcher = dcPattern.matcher(entry.getKey());
                        if (matcher.matches()) {
                            String region = matcher.group(1);
                            if ("enabled".equalsIgnoreCase(entry.getValue()) || Boolean
                                    .parseBoolean(entry.getValue())) {
                                String url = subscriptionInfo.getSettings().get("dc." + region + ".api.url");
                                if (url != null) {
                                    result.put(url, region.toUpperCase());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.INFO, "Could not get list of applications: ", e);
                }
                return result;
            }
        }
    }

    public static class Setting implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String key;

        private final String value;

        @DataBoundConstructor
        public Setting(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }
    }
}
