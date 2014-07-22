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
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Finds the first file that matches a file pattern.
 *
 * @since 4.0
 */
@SuppressWarnings("unused") // used by stapler
public class WildcardPathDeploySource extends DeploySource {
    private static final Logger LOGGER = Logger.getLogger(WildcardPathDeploySource.class.getName());

    /**
     * The file pattern.
     */
    @NonNull
    private final String filePattern;

    /**
     * Constructor used by stapler.
     *
     * @param filePattern the file pattern.
     */
    @DataBoundConstructor
    public WildcardPathDeploySource(@CheckForNull String filePattern) {
        this.filePattern = StringUtils.isBlank(filePattern) ? "**/*.war" : filePattern;
    }

    /**
     * Returns the file pattern.
     *
     * @return the file pattern.
     */
    @NonNull
    public String getFilePattern() {
        return filePattern;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CheckForNull
    public File getApplicationFile(@NonNull Run run) {
        File result = null;
        // TODO update for 1.532
        if (run.getArtifactsDir().isDirectory()) {
            FileSet fileSet = new FileSet();
            fileSet.setProject(new Project());
            fileSet.setDir(run.getArtifactsDir());
            fileSet.setIncludes(getFilePattern());
            try {
                String[] files = fileSet.getDirectoryScanner().getIncludedFiles();
                if (files.length > 0) {
                    result = new File(run.getArtifactsDir(), files[0]);
                }
            } catch (BuildException e) {
                LOGGER.log(Level.FINE, Messages.WildcardPathDeploySource_CouldNotListFromBuildArtifacts(
                        getFilePattern(), run), e);
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CheckForNull
    public FilePath getApplicationFile(@NonNull FilePath workspace) {
        try {
            FilePath[] list = workspace.list(getFilePattern());
            return list != null && list.length > 0 ? list[0] : null;
        } catch (IOException e) {
            LOGGER.log(Level.FINE,
                    Messages.WildcardPathDeploySource_CouldNotListFromWorkspace(getFilePattern(), workspace), e);
        } catch (InterruptedException e) {
            LOGGER.log(Level.FINER,
                    Messages.WildcardPathDeploySource_CouldNotListFromWorkspace(getFilePattern(), workspace), e);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public String toString() {
        return filePattern;
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

        WildcardPathDeploySource that = (WildcardPathDeploySource) o;

        if (!filePattern.equals(that.filePattern)) {
            return false;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return filePattern.hashCode();
    }

    /**
     * Descriptor for {@link WildcardPathDeploySource}
     */
    @Extension
    public static class DescriptorImpl extends DeploySourceDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.WildcardPathDeploySource_DisplayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isSupported(@CheckForNull DeploySourceOrigin source) {
            return true;
        }

        @Override
        public boolean isApplicable(@CheckForNull Class<? extends AbstractProject> jobType) {
            return !isMavenJob(jobType);
        }

        @SuppressWarnings("unused") // used by stapler
        public FormValidation doCheckFilePattern(@QueryParameter @RelativePath("..") final String fromWorkspace,
                                                 @QueryParameter final String targetDescriptorId,
                                                 @QueryParameter final String value)
                throws IOException, ServletException, InterruptedException {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.warning("You really should specify a pattern, otherwise '**/*.war' is assumed");
            }
            if (Boolean.valueOf(fromWorkspace)) {
                Job job = findJob();
                if (job != null && job instanceof AbstractProject) {
                    FilePath someWorkspace = ((AbstractProject) job).getSomeWorkspace();
                    if (someWorkspace == null) {
                        return FormValidation.warning("The workspace is empty. Unable to validate '" + value + "'.");
                    }
                    FilePath[] filePaths = someWorkspace.list(value);
                    if (filePaths.length > 1) {
                        return FormValidation.warning("Multiple files in the workspace match '" + value + "'");
                    }
                    if (filePaths.length == 1) {
                        return delegatePathValidationToTarget(value, targetDescriptorId, filePaths[0]);
                    }
                }
            }
            Run run = findRun();
            if (run != null) {
                if (!run.getArtifactsDir().isDirectory()) {
                    return FormValidation.error("There are no archived artifacts");
                }
                FileSet fileSet = new FileSet();
                fileSet.setProject(new Project());
                fileSet.setDir(run.getArtifactsDir());
                fileSet.setIncludes(value);
                int includedFilesCount = fileSet.getDirectoryScanner().getIncludedFilesCount();
                if (includedFilesCount > 1) {
                    return FormValidation.warning("Multiple archived files match '" + value + "'");
                }
                if (includedFilesCount == 1) {
                    return FormValidation.ok();
                }
            }
            return FormValidation.warning("Could not find a file that matches '" + value + "'");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public DeploySource newInstance() {
            return new WildcardPathDeploySource("**/*.war");
        }
    }
}
