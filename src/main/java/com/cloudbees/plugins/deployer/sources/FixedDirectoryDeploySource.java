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

package com.cloudbees.plugins.deployer.sources;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.RelativePath;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * A deploy source that returns a specific directory.
 *
 * @since 4.3
 */
public class FixedDirectoryDeploySource extends DeploySource {
    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(FixedDirectoryDeploySource.class.getName());

    /**
     * The fixed file path.
     */
    @NonNull
    private final String directoryPath;

    /**
     * Constructor used by stapler.
     *
     * @param directoryPath the file path.
     */
    @DataBoundConstructor
    public FixedDirectoryDeploySource(@CheckForNull String directoryPath) {
        this.directoryPath = StringUtils.trimToEmpty(directoryPath);
    }

    /**
     * Returns the file path.
     *
     * @return the file path.
     */
    @NonNull
    public String getDirectoryPath() {
        return directoryPath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CheckForNull
    public File getApplicationFile(@NonNull Run run) {
        return null; // only support workspace sources
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public FilePath getApplicationFile(@NonNull FilePath workspace) {
        return directoryPath == null ? workspace : workspace.child(directoryPath);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public String toString() {
        return directoryPath;
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

        FixedDirectoryDeploySource that = (FixedDirectoryDeploySource) o;

        if (!directoryPath.equals(that.directoryPath)) {
            return false;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return directoryPath.hashCode();
    }

    /**
     * Descriptor for {@link StaticSelectionDeploySource}.
     */
    @Extension
    public static class DescriptorImpl extends DeploySourceDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isSupported(@CheckForNull DeploySourceOrigin origin) {
            return DeploySourceOrigin.WORKSPACE.equals(origin);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isDirectorySource() {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isFileSource() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Fixed directory";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public DeploySource newInstance() {
            return new FixedDirectoryDeploySource(null);
        }

        /**
         * Checks the specified directory path.
         *
         * @param fromWorkspace whether validation is being performed from the workspace or some other source.
         * @param value         the directory path to validate.
         * @return the results of validation.
         * @throws IOException          when things go wrong.
         * @throws ServletException     when things go wrong.
         * @throws InterruptedException when things go wrong.
         */
        @SuppressWarnings("unused") // used by stapler
        public FormValidation doCheckDirectoryPath(@QueryParameter @RelativePath("..") final String fromWorkspace,
                                                   @QueryParameter final String targetDescriptorId,
                                                   @QueryParameter final String value)
                throws IOException, ServletException, InterruptedException {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.warning("You really should specify a directory, otherwise '.' is assumed");
            }
            if (Boolean.valueOf(fromWorkspace)) {
                Job job = findJob();
                if (job != null && job instanceof AbstractProject) {
                    FilePath someWorkspace = ((AbstractProject) job).getSomeWorkspace();
                    if (someWorkspace == null) {
                        return FormValidation.warning("The workspace is empty. Unable to validate '" + value + "'.");
                    }

                    FilePath dirPath = someWorkspace.child(value);
                    if (dirPath.exists()) {
                        if (dirPath.isDirectory()) {
                            return delegatePathValidationToTarget(value, targetDescriptorId, dirPath);
                        }
                        return FormValidation.error("The specified path, '" + value + "' currently is a file.");
                    }
                    return FormValidation.warning("The specified path '" + value + "' currently does not exist.");
                }
            }
            return FormValidation.warning("Could not find a directory that matches '" + value + "'");
        }
    }
}
