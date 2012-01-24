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

package com.cloudbees.plugins.deployer.targets;

import com.cloudbees.plugins.deployer.sources.DeploySource;
import com.cloudbees.plugins.deployer.sources.DeploySourceDescriptor;
import com.cloudbees.plugins.deployer.sources.DeploySourceOrigin;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.util.FormValidation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The base class for {@link DeployTarget} descriptors
 *
 * @param <T> the {@link DeployTarget class}.
 * @since 4.0
 */
public abstract class DeployTargetDescriptor<T extends DeployTarget<T>> extends Descriptor<T> {

    /**
     * Infers the type of the corresponding {@link DeployTarget} from the outer class.
     * This version works when you follow the common convention, where a descriptor
     * is written as the static nested class of the describable class.
     */
    protected DeployTargetDescriptor() {
    }

    /**
     * For cases where the common convention cannot be followed.
     *
     * @param clazz the type of {@link DeployTarget} that this descriptor describes.
     */
    protected DeployTargetDescriptor(@NonNull Class<? extends T> clazz) {
        super(clazz);
    }

    /**
     * Returns {@code true} if and only if this target can accept files.
     *
     * @return {@code true} if and only if this target can accept files.
     * @since 4.3
     */
    public boolean isFileTarget() {
        return true;
    }

    /**
     * Returns {@code true} if and only if this target can accept directories.
     *
     * @return {@code true} if and only if this target can accept directories.
     * @since 4.3
     */
    public boolean isDirectoryTarget() {
        return false;
    }

    /**
     * Gets all the {@link DeploySource} descriptors.
     *
     * @return all the {@link DeploySource} descriptors.
     */
    @SuppressWarnings("unused") // used by stapler
    @NonNull
    public DescriptorExtensionList<DeploySource, Descriptor<DeploySource>> getDeploySourceDescriptors() {
        return Hudson.getInstance().getDescriptorList(DeploySource.class);
    }

    /**
     * Returns the {@link DeploySourceDescriptor}s that are valid for the specific context.
     *
     * @param origins the valid origins.
     * @param jobType the project type.
     * @return the {@link DeploySourceDescriptor}s that are valid for the specific context.
     */
    @SuppressWarnings("unused") // used by stapler
    @NonNull
    public List<DeploySourceDescriptor> getDeploySourceDescriptors(@CheckForNull Set<DeploySourceOrigin> origins,
                                                                   @CheckForNull Class<? extends AbstractProject>
                                                                           jobType) {
        List<DeploySourceDescriptor> result = new ArrayList<DeploySourceDescriptor>();
        if (origins != null) {
            for (Descriptor<DeploySource> d : Hudson.getInstance().getDescriptorList(DeploySource.class)) {
                if (d instanceof DeploySourceDescriptor) {
                    DeploySourceDescriptor descriptor = (DeploySourceDescriptor) d;
                    for (DeploySourceOrigin source : origins) {
                        if (descriptor.isSupported(source) && descriptor.isApplicable(jobType)) {
                            if ((isFileTarget() && descriptor.isFileSource())
                                    || (isDirectoryTarget() && descriptor.isDirectorySource())) {
                                result.add(descriptor);
                            }
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * If the specified source is null, try to pick the best default to select initially.
     *
     * @param source  the possibly null source.
     * @param origins the origins from which sources can be considered valid.
     * @param jobType the project type.
     * @return either the passed through source, or the (possible {@code null}) best guess default.
     */
    @CheckForNull
    public DeploySource defaultDeploySource(@CheckForNull DeploySource source,
                                            @CheckForNull Set<DeploySourceOrigin> origins,
                                            @CheckForNull Class<? extends AbstractProject> jobType) {
        if (source != null) {
            return source;
        }
        if (origins != null) {
            for (Descriptor<DeploySource> d : Hudson.getInstance().getDescriptorList(DeploySource.class)) {
                if (d instanceof DeploySourceDescriptor) {
                    DeploySourceDescriptor descriptor = (DeploySourceDescriptor) d;
                    for (DeploySourceOrigin origin : origins) {
                        if (descriptor.isSupported(origin) && descriptor.isApplicable(jobType)) {
                            if ((isFileTarget() && descriptor.isFileSource())
                                    || (isDirectoryTarget() && descriptor.isDirectorySource())) {
                                try {
                                    return descriptor.newInstance();
                                } catch (UnsupportedOperationException e) {
                                    // ignore
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * An extra validation hook for {@link DeployTarget} instances to provide additional checks on the user's specified
     * {@link DeploySource} when resolving from the {@link DeploySourceOrigin#WORKSPACE}.
     *
     * @param filePathName the user specified file path name value.
     * @param filePath     the resulting resolved file path.
     * @return the validation results.
     * @since 4.3
     */
    @SuppressWarnings("unused") // used by stapler
    public FormValidation validateFilePath(String filePathName, FilePath filePath)
            throws IOException, InterruptedException {
        return FormValidation.ok();
    }
}
