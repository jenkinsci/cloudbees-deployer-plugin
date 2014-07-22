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

import com.cloudbees.plugins.deployer.DeployEvent;
import com.cloudbees.plugins.deployer.DeployListener;
import com.cloudbees.plugins.deployer.exceptions.DeployException;
import com.cloudbees.plugins.deployer.exceptions.DeploySourceNotFoundException;
import com.cloudbees.plugins.deployer.hosts.DeployHost;
import com.cloudbees.plugins.deployer.records.DeployedApplicationAction;
import com.cloudbees.plugins.deployer.records.DeployedApplicationFingerprintFacet;
import com.cloudbees.plugins.deployer.records.DeployedApplicationLocation;
import com.cloudbees.plugins.deployer.sources.DeploySource;
import com.cloudbees.plugins.deployer.sources.DeploySourceOrigin;
import com.cloudbees.plugins.deployer.targets.DeployTarget;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Fingerprint;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A deployment engine knows how to deploy artifacts to a remote service.
 */
public abstract class Engine<S extends DeployHost<S, T>, T extends DeployTarget<T>> {

    protected final Item deployScope;
    protected final List<Authentication> deployAuthentications;
    protected final AbstractBuild<?, ?> build;
    protected final S set;
    protected final Launcher launcher;
    protected final BuildListener listener;
    protected final Set<DeploySourceOrigin> sources;

    protected Engine(EngineConfiguration<S, T> config) {
        final List<Authentication> deployAuthentications = config.getDeployAuthentications();
        this.deployAuthentications = deployAuthentications == null
                ? Arrays.asList(ACL.SYSTEM)
                : new ArrayList<Authentication>(deployAuthentications);
        this.deployScope = config.getDeployScope();
        this.build = config.getBuild();
        this.launcher = config.getLauncher();
        this.listener = config.getListener();
        this.set = config.getConfiguration();
        final Set<DeploySourceOrigin> sources = config.getSources();
        this.sources = sources == null ? new HashSet<DeploySourceOrigin>() : new HashSet<DeploySourceOrigin>(
                sources);
    }

    public boolean perform() throws Throwable {
        final List<DeploySourceOrigin> validOrigins = new ArrayList<DeploySourceOrigin>(
                DeploySourceOrigin.allInPreferenceOrder());
        validOrigins.retainAll(sources);

        logDetails();
        for (T target : set.getTargets()) {
            log("Deploying " + target.getDisplayName());
            boolean found = false;
            DeployEvent event = createEvent(target);
            try {
                DeploySource source = target.getArtifact();
                if (source == null) {
                    throw new DeploySourceNotFoundException(source,
                            "Undefined source for " + target.getDisplayName());
                }
                DeployedApplicationLocation location = null;
                findSource:
                for (DeploySourceOrigin origin : validOrigins) {
                    if (source.getDescriptor().isSupported(origin)) {
                        switch (origin) {
                            case WORKSPACE: {
                                FilePath applicationFile = source.getApplicationFile(build.getWorkspace());
                                if (applicationFile != null) {
                                    found = true;
                                    validate(applicationFile);
                                    log("  Resolved from workspace as " + applicationFile);
                                    location = process(applicationFile, target);
                                    DeployListener.notifySuccess(set, target, event);
                                    break findSource;
                                }
                            }
                            break;
                            case RUN: {
                                File applicationFile = source.getApplicationFile(build);
                                if (applicationFile != null) {
                                    found = true;
                                    validate(applicationFile);
                                    log("  Resolved from archived artifacts as " + applicationFile);
                                    location = process(applicationFile, target);
                                    // TODO delete applicationFile
                                    DeployListener.notifySuccess(set, target, event);
                                    break findSource;
                                }
                            }
                            break;
                            default:
                                DeployListener.notifyFailure(set, target, event);
                                throw new UnsupportedOperationException(
                                        "Unknown DeploySourceOrigin instance: " + origin);
                        }
                    }
                }
                if (!found) {
                    throw new DeploySourceNotFoundException(source,
                            "Cannot find source for " + target.getDisplayName());
                }
                if (location != null) {
                    boolean haveAction = false;
                    for (DeployedApplicationAction action : build.getActions(DeployedApplicationAction.class)) {
                        if (action.getLocation().equals(location)) {
                            haveAction = true;
                            break;
                        }
                    }
                    if (!haveAction) {
                        build.addAction(new DeployedApplicationAction<DeployedApplicationLocation>(location));
                    }
                }
            } catch (RuntimeException e) {
                DeployListener.notifyFailure(set, target, event);
                throw e;
            } catch (DeployException e) {
                DeployListener.notifyFailure(set, target, event);
                throw e;
            }
        }
        return true;
    }

    public abstract void validate(FilePath applicationFile) throws DeployException;

    public abstract void validate(File applicationFile) throws DeployException;

    @CheckForNull
    public DeployedApplicationLocation process(FilePath applicationFile, T target) throws DeployException {
        try {
            FingerprintingWrapper actor = new FingerprintingWrapper(newDeployActor(target), listener);
            return applicationFile.act(actor);
        } catch (DeployException e) {
            throw e;
        } catch (InterruptedException e) {
            throw new DeployException("Deployment interrupted", e);
        } catch (Throwable t) {
            throw new DeployException(t.getMessage(), t);
        }
    }

    @CheckForNull
    public DeployedApplicationLocation process(File applicationFile, T target) throws DeployException {
        try {
            FingerprintingWrapper actor = new FingerprintingWrapper(newDeployActor(target), listener);
            return actor.invoke(applicationFile, launcher.getChannel());
        } catch (DeployException e) {
            throw e;
        } catch (InterruptedException e) {
            throw new DeployException("Deployment interrupted", e);
        } catch (Throwable t) {
            throw new DeployException(t.getMessage(), t);
        }
    }

    protected abstract FilePath.FileCallable<DeployedApplicationLocation> newDeployActor(T target)
            throws DeployException;

    public abstract DeployEvent createEvent(T target) throws DeployException;

    public abstract void logDetails();

    public void log(String message) {
        listener.getLogger().println("[cloudbees-deployer] " + message);
    }

    @SuppressWarnings("unchecked")
    public static EngineFactory<?, ?> create(DeployHost<?, ?> configuration) throws DeployException {
        for (Object d : Jenkins.getInstance().getDescriptorList(EngineFactory.class)) {
            if (d instanceof EngineFactoryDescriptor) {
                final EngineFactoryDescriptor<?, ?> descriptor = EngineFactoryDescriptor.class.cast(d);
                if (descriptor.isApplicable(configuration.getClass())) {
                    return ((EngineFactoryDescriptor) descriptor).newFactory(configuration);
                }
            }

        }
        throw new DeployException("Deployment hosts of type " + configuration.getClass() + " are unsupported");
    }

    public static class FingerprintDecorator implements Callable<DeployedApplicationLocation, IOException> {

        private final String md5sum;
        private final DeployedApplicationLocation location;
        private final BuildListener listener;
        private final long timestamp;

        public FingerprintDecorator(BuildListener listener, String md5sum, DeployedApplicationLocation location) {
            this.listener = listener;
            this.md5sum = md5sum;
            this.location = location;
            this.timestamp = System.currentTimeMillis();
        }

        public DeployedApplicationLocation call() throws IOException {
            Fingerprint fingerprint = Hudson.getInstance()._getFingerprint(md5sum);
            if (fingerprint != null) {
                fingerprint.getFacets()
                        .add(new DeployedApplicationFingerprintFacet<DeployedApplicationLocation>(fingerprint,
                                timestamp,
                                location));
                fingerprint.save();
                listener.getLogger().println("[cloudbees-deployer] Recorded deployment in fingerprint record");
            } else {
                listener.getLogger()
                        .println("[cloudbees-deployer] Deployed artifact does not have a fingerprint record");
            }
            return location;
        }
    }

    public static class FingerprintingWrapper implements FilePath.FileCallable<DeployedApplicationLocation> {
        private final FilePath.FileCallable<DeployedApplicationLocation> delegate;
        private final BuildListener listener;

        public FingerprintingWrapper(FilePath.FileCallable<DeployedApplicationLocation> delegate,
                                     BuildListener listener) {
            this.delegate = delegate;
            this.listener = listener;
        }

        public DeployedApplicationLocation invoke(File f, VirtualChannel channel)
                throws IOException, InterruptedException {
            DeployedApplicationLocation location = delegate.invoke(f, channel);
            if (f.isFile() && location != null) {
                FileInputStream fis = new FileInputStream(f);
                try {
                    String md5sum = Util.getDigestOf(fis);
                    FingerprintDecorator decorator = new FingerprintDecorator(listener, md5sum, location);
                    Jenkins instance = Jenkins.getInstance();
                    if (instance != null) {
                        return decorator.call();
                    } else {
                        Channel current = Channel.current();
                        assert current != null
                                : "Can only have a null Jenkins instance if on the other end of a channel";
                        return current.call(decorator);
                    }
                } catch (Exception e) {
                    listener.error("[cloudbees-deployer] Could not record fingerprint association");
                    // e.printStackTrace(listener.getLogger());
                } catch (LinkageError e) {
                    listener.getLogger().println(
                            "[cloudbees-deployer] Cannot not record fingerprint association prior to Jenkins 1.421");
                } finally {
                    IOUtils.closeQuietly(fis);
                }

            }
            return location;
        }
    }
}
