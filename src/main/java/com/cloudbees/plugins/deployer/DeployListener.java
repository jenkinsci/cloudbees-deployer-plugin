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
import com.cloudbees.plugins.deployer.targets.DeployTarget;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An extension point for listening to deployment events
 */
public class DeployListener implements ExtensionPoint {
    /**
     * Our logger
     */
    private static final Logger LOGGER = Logger.getLogger(DeployListener.class.getName());

    /**
     * Called for each successful deployment event.
     *
     * @param event the successful deployment details.
     * @deprecated preference is to override {@link #onSuccess(com.cloudbees.plugins.deployer.hosts.DeployHost,
     *             com.cloudbees.plugins.deployer.targets.DeployTarget, DeployEvent)} instead
     */
    @Deprecated
    public void onSuccess(DeployEvent event) {
    }

    /**
     * Called for each successful deployment event.
     *
     * @param host   the host that the deployment was for (or {@code null} if the information is not available).
     * @param target the target of the deployment (or {@code null} if the information is not available).
     * @param event  the successful deployment details.
     * @since 4.2
     */
    @SuppressWarnings("deprecation")
    public <S extends DeployHost<S, T>, T extends DeployTarget<T>> void onSuccess(@CheckForNull DeployHost<S, T> host,
                                                                                  @CheckForNull DeployTarget<T> target,
                                                                                  @NonNull DeployEvent event) {
        onSuccess(event); // pass through for legacy code
    }

    /**
     * Called for each failed deployment event.
     *
     * @param event the failed deployment details.
     * @deprecated preference is to override {@link #onFailure(com.cloudbees.plugins.deployer.hosts.DeployHost,
     *             com.cloudbees.plugins.deployer.targets.DeployTarget, DeployEvent)} instead
     */
    @Deprecated
    public void onFailure(DeployEvent event) {
    }

    /**
     * Called for each failed deployment event.
     *
     * @param host   the host that the deployment was for (or {@code null} if the information is not available).
     * @param target the target of the deployment (or {@code null} if the information is not available).
     * @param event  the failed deployment details.
     * @since 4.2
     */
    @SuppressWarnings("deprecation")
    public <S extends DeployHost<S, T>, T extends DeployTarget<T>> void onFailure(@CheckForNull DeployHost<S, T> host,
                                                                                  @CheckForNull DeployTarget<T> target,
                                                                                  @NonNull DeployEvent event) {
        onFailure(event);
    }

    /**
     * Notifies all the listeners of a successful deployment.
     *
     * @param event the deployment details.
     * @deprecated use {@link #notifySuccess(com.cloudbees.plugins.deployer.hosts.DeployHost,
     *             com.cloudbees.plugins.deployer.targets.DeployTarget, DeployEvent)}
     */
    @Deprecated
    public static void notifySuccess(DeployEvent event) {
        notifySuccess((DeployHost) null, (DeployTarget) null, event);
    }

    /**
     * Notifies all the listeners of a successful deployment.
     *
     * @param host   the host that the deployment was for (or {@code null} if the information is not available).
     * @param target the target of the deployment (or {@code null} if the information is not available).
     * @param event  the deployment details.
     * @since 4.2
     */
    @SuppressWarnings("unchecked")
    public static <S extends DeployHost<S, T>, T extends DeployTarget<T>> void notifySuccess(
            @CheckForNull DeployHost<S, T> host,
            @CheckForNull DeployTarget<T> target,
            @NonNull DeployEvent event) {
        for (DeployListener listener : Jenkins.getInstance().getExtensionList(DeployListener.class)) {
            try {
                listener.onSuccess(host, target, event);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Uncaught exception from " + listener.getClass(), t);
            }
        }
    }

    /**
     * Notifies all the listeners of a failed deployment.
     *
     * @param event the deployment details.
     * @deprecated use {@link #notifyFailure(com.cloudbees.plugins.deployer.hosts.DeployHost,
     *             com.cloudbees.plugins.deployer.targets.DeployTarget, DeployEvent)}
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public static void notifyFailure(DeployEvent event) {
        notifyFailure((DeployHost) null, (DeployTarget) null, event);
    }

    /**
     * Notifies all the listeners of a failed deployment.
     *
     * @param host   the host that the deployment was for (or {@code null} if the information is not available).
     * @param target the target of the deployment (or {@code null} if the information is not available).
     * @param event  the deployment details.
     * @since 4.2
     */
    public static <S extends DeployHost<S, T>, T extends DeployTarget<T>> void notifyFailure(
            @CheckForNull DeployHost<S, T> host,
            @CheckForNull DeployTarget<T> target,
            @NonNull DeployEvent event) {
        for (DeployListener listener : Jenkins.getInstance().getExtensionList(DeployListener.class)) {
            try {
                listener.onFailure(host, target, event);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Uncaught exception from " + listener.getClass(), t);
            }
        }
    }
}
