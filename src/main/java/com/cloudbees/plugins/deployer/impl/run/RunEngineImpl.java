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

import com.cloudbees.api.ApplicationDeployArchiveResponse;
import com.cloudbees.api.ApplicationDeployArgs;
import com.cloudbees.api.BeesClient;
import com.cloudbees.api.BeesClientConfiguration;
import com.cloudbees.api.UploadProgress;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesAccount;
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesUser;
import com.cloudbees.plugins.deployer.DeployEvent;
import com.cloudbees.plugins.deployer.engines.Engine;
import com.cloudbees.plugins.deployer.engines.EngineConfiguration;
import com.cloudbees.plugins.deployer.engines.EngineFactory;
import com.cloudbees.plugins.deployer.engines.EngineFactoryDescriptor;
import com.cloudbees.plugins.deployer.exceptions.DeployException;
import com.cloudbees.plugins.deployer.hosts.DeployHost;
import com.cloudbees.plugins.deployer.records.DeployedApplicationLocation;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Hudson;
import hudson.remoting.VirtualChannel;
import hudson.util.IOException2;
import hudson.util.TimeUnit2;
import net.jcip.annotations.Immutable;
import org.acegisecurity.Authentication;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.io.FilenameUtils;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The {@link com.cloudbees.plugins.deployer.engines.Engine} for deploying to CloudBees RUN@cloud.
 */
@SuppressWarnings("unused")
public class RunEngineImpl extends Engine<RunHostImpl, RunTargetImpl> {

    private final CloudBeesUser user;
    private final CloudBeesAccount account;

    protected RunEngineImpl(EngineConfiguration<RunHostImpl, RunTargetImpl> factory)
            throws DeployException {
        super(factory);
        CloudBeesUser user = null;
        authenticationSearch:
        for (Authentication authentication : deployAuthentications) {
            for (CloudBeesUser u : CredentialsProvider
                    .lookupCredentials(CloudBeesUser.class, deployScope, authentication)) {
                if (u.getName().equals(set.getUser())) {
                    user = u;
                    break authenticationSearch;
                }
            }
        }
        if (user == null) {
            throw new DeployException("Cannot find user " + set.getUser());
        }

        CloudBeesAccount account = user.getAccount(set.getAccount());
        if (account == null) {
            throw new DeployException(
                    set.getUser() + " does not seem to belong to the " + set.getAccount()
                            + " account");
        }
        this.user = user;
        this.account = account;
    }

    private static String toUsAscii(String s) {
        try {
            byte[] src = com.ibm.icu.text.Normalizer.normalize(s, com.ibm.icu.text.Normalizer.NFD).getBytes("US-ASCII");
            byte[] dst = new byte[src.length];
            int dstIndex = 0;
            for (byte srcByte : src) {
                if ((srcByte & 0xFF) < 127) {
                    dst[dstIndex++] = srcByte;
                }
            }
            return new String(dst, 0, dstIndex, "US-ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("US-ASCII is one of the standard charsets in the JVM", e);
        }
    }

    @Override
    public void validate(FilePath applicationFile) throws DeployException {
        try {
            if (!Boolean.TRUE.equals(applicationFile.act(new IsFileCallable()))) {
                throw new DeployException("Not a valid archive for deployment: " + applicationFile);
            }
        } catch (InterruptedException e) {
            throw new DeployException(e.getMessage(), e);
        } catch (IOException e) {
            throw new DeployException(e.getMessage(), e);
        }
    }

    @Override
    public void validate(File applicationFile) throws DeployException {
        if (!applicationFile.isFile()) {
            throw new DeployException("Not a valid archive for deployment: " + applicationFile);
        }
    }

    @Override
    public DeployEvent createEvent(RunTargetImpl target) throws DeployException {
        try {
            return new EventImpl(build, build.getCauses(), user, account, target.getApplicationId(build, listener));
        } catch (InterruptedException e) {
            throw new DeployException("Could not create DeployEvent", e);
        } catch (IOException e) {
            throw new DeployException("Could not create DeployEvent", e);
        } catch (MacroEvaluationException e) {
            throw new DeployException("Could not create DeployEvent", e);
        } catch (Throwable t) {
            throw new DeployException("Could not create DeployEvent", t);
        }
    }

    @Override
    protected FilePath.FileCallable<DeployedApplicationLocation> newDeployActor(RunTargetImpl target)
            throws DeployException {
        try {
            return new DeployFileCallable(build, listener, user, account, target,
                    target.getApplicationConfigMap(build, listener));
        } catch (InterruptedException e) {
            throw new DeployException("Deployment interrupted", e);
        } catch (IOException e) {
            throw new DeployException(e.getMessage(), e);
        } catch (MacroEvaluationException e) {
            throw new DeployException(e.getMessage(), e);
        } catch (Throwable t) {
            throw new DeployException(t.getMessage(), t);
        }
    }

    @Override
    public void logDetails() {
        log("Deploying as " + set.getUser() + " to the " + set.getAccount() + " account");
    }

    @SuppressWarnings("unused")
    public static class FactoryImpl extends EngineFactory<RunHostImpl, RunTargetImpl> {

        public FactoryImpl(@NonNull RunHostImpl configuration) {
            super(configuration);
        }

        @NonNull
        public RunEngineImpl build() throws DeployException {
            return new RunEngineImpl(getConfiguration());
        }

        @Extension
        @SuppressWarnings("unused")
        public static class DescriptorImpl extends EngineFactoryDescriptor<RunHostImpl, RunTargetImpl> {

            @Override
            public String getDisplayName() {
                return Messages.RunHostImpl_DisplayName();
            }

            @Override
            public boolean isApplicable(Class<? extends DeployHost> aClass) {
                return RunHostImpl.class.isAssignableFrom(aClass);
            }

            @Override
            public FactoryImpl newFactory(RunHostImpl configuration) {
                return new FactoryImpl(configuration);
            }
        }

    }

    @Immutable
    public static class EventImpl extends DeployEvent {

        @NonNull
        private final CloudBeesUser user;
        @NonNull
        private final CloudBeesAccount account;
        @NonNull
        private final String applicationId;

        public EventImpl(@NonNull AbstractBuild<?, ?> build, @NonNull List<Cause> causes,
                         @NonNull CloudBeesUser user, @NonNull CloudBeesAccount account,
                         @NonNull String applicationId) {
            super(build, causes);
            user.getClass();
            account.getClass();
            applicationId.getClass();
            this.user = user;
            this.account = account;
            this.applicationId = applicationId;
        }

        @NonNull
        @SuppressWarnings("unused")
        public CloudBeesAccount getAccount() {
            return account;
        }

        @NonNull
        @SuppressWarnings("unused")
        public String getApplicationId() {
            return applicationId;
        }

        @NonNull
        @SuppressWarnings("unused")
        public CloudBeesUser getUser() {
            return user;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }

            EventImpl event = (EventImpl) o;

            if (!account.equals(event.account)) {
                return false;
            }
            if (!applicationId.equals(event.applicationId)) {
                return false;
            }
            if (!user.equals(event.user)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + applicationId.hashCode();
            return result;
        }
    }

    public static class DeployFileCallable implements FilePath.FileCallable<DeployedApplicationLocation> {

        private static final long serialVersionUID = 1L;

        private final BuildListener listener;
        private final String apiKey;
        private final String secret;
        private final String server;
        private final String environment;
        private final String description;
        private final String appId;
        private final Map<String, String> config;
        private final String clickStackName;
        private final Map<String, String> clickStackConfig;
        private final boolean deltaDeployment;
        private final Map<String, String> clickStackRuntimeConfig;

        public DeployFileCallable(AbstractBuild<?, ?> build, BuildListener listener, CloudBeesUser user,
                                  CloudBeesAccount account, RunTargetImpl target, Map<String, String> config)
                throws MacroEvaluationException, IOException, InterruptedException {
            this.listener = listener;
            this.config = config == null ? null : new HashMap<String, String>(config);
            apiKey = user.getAPIKey();
            secret = user.getAPISecret().getPlainText();
            server = target.getApiEndPoint();
            environment = target.getApplicationEnvironment(build, listener);
            description = target.getDeploymentDescription(build, listener);
            appId = account.getName() + "/" + target.getApplicationId(build, listener);
            clickStackName = target.getClickStackName(build, listener);
            clickStackConfig = target.getClickStackConfigMap(build, listener);
            clickStackRuntimeConfig = target.getClickStackRuntimeConfigMap(build, listener);
            deltaDeployment = target.isDeltaDeployment();
        }

        public DeployedApplicationLocation invoke(File f, VirtualChannel channel)
                throws IOException, InterruptedException {
            listener.getLogger().println("[cloudbees-deployer] Deploying via API server at " + server);
            DeployedApplicationLocation result = null;
            String description = this.description;
            BeesClientConfiguration clientConfig = new BeesClientConfiguration(server, apiKey, secret, "xml", "1.0");
            if (Hudson.getInstance() != null && Hudson.getInstance().proxy != null) {
                final ProxyConfiguration proxy = Hudson.getInstance().proxy;
                clientConfig.setProxyHost(proxy.name);
                clientConfig.setProxyPort(proxy.port);
                clientConfig.setProxyUser(proxy.getUserName());
                clientConfig.setProxyPassword(proxy.getPassword());
            }
            HttpClientParams httpClientParams = new HttpClientParams();
            httpClientParams.setSoTimeout((int) TimeUnit2.MINUTES.toMillis(30)); // Fabian says use 30 min
            clientConfig.setHttpClientParams(httpClientParams);
            BeesClient client = new BeesClient(clientConfig);
            try {
                String description1 = toUsAscii(description);
                if (!description.equals(description1)) {
                    listener.getLogger().println("[cloudbees-deployer] Description '" + description
                            + "' contained unsupported characters, using '" + description1 + "' instead.");
                    description = description1;
                }
                Map<String, String> parameters = new HashMap<String, String>();
                for (Map.Entry<String, String> e : clickStackConfig.entrySet()) {
                    String key = Util.fixEmptyAndTrim(e.getKey());
                    if (key != null) {
                        parameters.put(key, Util.fixNull(e.getValue()));
                    }
                }
                if (clickStackName != null) {
                    parameters.put("containerType", clickStackName);
                }
                for (Map.Entry<String, String> e : clickStackRuntimeConfig.entrySet()) {
                    String key = Util.fixEmptyAndTrim(e.getKey());
                    if (key != null) {
                        parameters.put("runtime."+key, Util.fixNull(e.getValue()));
                    }
                }

                ApplicationDeployArgs deployArgs = new ApplicationDeployArgs.Builder(appId)
                        .environment(environment)
                        .description(description)
                        .deployPackage(f, FilenameUtils.getExtension(f.getPath()))
                        .srcFile((File) null)
                        .incrementalDeployment(deltaDeployment)
                        .withVars(config)
                        .withParams(parameters)
                        .withProgressFeedback(new ConsoleListenerUploadProgress(listener, f.length()))
                        .build();
                ApplicationDeployArchiveResponse response = client.applicationDeployArchive(deployArgs);
                result = new RunDeployedApplicationLocation(response.getId(), environment, response.getUrl());
                listener.getLogger().println(
                        MessageFormat.format("[cloudbees-deployer] Deployed to application id {0}", response.getId()));
                listener.getLogger()
                        .println(
                                MessageFormat.format("[cloudbees-deployer] Can be accessed at {0}", response.getUrl()));
            } catch (Exception e) {
                throw new IOException2(e.getMessage(), e);
            }
            return result;
        }

    }

    private static class ConsoleListenerUploadProgress
            implements UploadProgress {
        private long lastSignificant = 0;
        private long lastValue = -1;
        private String lastUnits = null;

        private long nextProgress = Long.MIN_VALUE;

        private static long ONE_K = 1024L;

        private final BuildListener listener;

        private final long length;

        ConsoleListenerUploadProgress(BuildListener buildListener, long length) {
            this.listener = buildListener;
            this.length = length;
        }

        public synchronized void handleBytesWritten(long deltaCount, long totalWritten, long totalToSend) {
            // output progress every 5% or 30s
            if (lastSignificant + Math.max(length / 20, 512) < totalWritten
                    || nextProgress < System.currentTimeMillis()) {
                long value;
                String units;
                if (Math.max(totalWritten, length) <= ONE_K * 8) {
                    value = totalWritten;
                    units = "B";
                } else if (Math.max(totalWritten, length) < ONE_K * ONE_K * 8) {
                    value = totalWritten / ONE_K;
                    units = "KB";
                } else {
                    value = totalWritten / ONE_K / ONE_K;
                    units = "MB";
                }
                if (value != lastValue || !units.equals(lastUnits)) {
                    listener.getLogger().println(MessageFormat.format("[cloudbees-deployer] {0} {1}", value, units));
                    lastValue = value;
                    lastUnits = units;
                }
                lastSignificant = totalWritten;
                nextProgress = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(15);
            }
        }
    }

    private static class IsFileCallable implements FilePath.FileCallable<Boolean> {
        public Boolean invoke(File f, VirtualChannel channel) throws IOException {
            return f.isFile();
        }
    }
}
