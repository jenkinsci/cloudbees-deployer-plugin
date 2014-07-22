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
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Finds the first file that matches an fixed selection.
 *
 * @since 4.0
 */
@SuppressWarnings("unused") // used by stapler
public class StaticSelectionDeploySource extends DeploySource {
    private static final Logger LOGGER = Logger.getLogger(StaticSelectionDeploySource.class.getName());
    /**
     * The fixed file path.
     */
    @NonNull
    private final String filePath;

    /**
     * Constructor used by stapler.
     *
     * @param filePath the file path.
     */
    @DataBoundConstructor
    public StaticSelectionDeploySource(@CheckForNull String filePath) {
        this.filePath = StringUtils.trimToEmpty(filePath);
    }

    /**
     * Returns the file path.
     *
     * @return the file path.
     */
    @NonNull
    public String getFilePath() {
        return filePath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CheckForNull
    public File getApplicationFile(@NonNull Run run) {
        // TODO update for 1.532
        if (run.getArtifactsDir().isDirectory()) {
            File file = new File(run.getArtifactsDir(), filePath);
            return file.exists() ? file : null;
        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public FilePath getApplicationFile(@NonNull FilePath workspace) {
        return workspace.child(filePath);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public String toString() {
        return filePath;
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

        StaticSelectionDeploySource that = (StaticSelectionDeploySource) o;

        if (!filePath.equals(that.filePath)) {
            return false;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return filePath.hashCode();
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
        public String getDisplayName() {
            return Messages.StaticSelectionDeploySource_DisplayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isSupported(@CheckForNull DeploySourceOrigin source) {
            return DeploySourceOrigin.RUN.equals(source);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return !isMavenJob(jobType);
        }

        @SuppressWarnings("unused") // used by stapler
        public FormValidation doCheckFilePath(@QueryParameter final String value)

                throws IOException, ServletException, InterruptedException {
            Run run = findRun();
            if (run != null) {
                if (!run.getHasArtifacts()) {
                    return FormValidation.warning(
                            "No artifacts were archived in the last successful run, unable to validate '" + value
                                    + "'");
                }
                if (new File(run.getArtifactsDir(), value).isFile()) {
                    return FormValidation.ok();
                }
            }
            return FormValidation.warning("Could not find a match for '" + value + "'");
        }

        @SuppressWarnings("unused") // used by stapler
        public ListBoxModel doFillFilePathItems()
                throws IOException, InterruptedException {

            Run run = findRun();

            ListBoxModel m = new ListBoxModel();

            if (run != null) {
                FileSet fileSet = new FileSet();
                fileSet.setProject(new Project());
                fileSet.setDir(run.getArtifactsDir());
                fileSet.setIncludes("**/*.war");
                for (String path : fileSet.getDirectoryScanner().getIncludedFiles()) {
                    m.add(path);
                }
            }

            return m;
        }

        @Override
        public DeploySource newInstance() {
            return new StaticSelectionDeploySource(null);
        }
    }
}
