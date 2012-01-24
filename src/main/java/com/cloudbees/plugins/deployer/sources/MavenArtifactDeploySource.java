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
import hudson.Util;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.reporters.MavenArtifact;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.logging.Logger;

/**
 * Finds artifacts from a {@link MavenBuild} based on GACT coordinates.
 *
 * @since 4.0
 */
@SuppressWarnings("unused") // used by stapler
public class MavenArtifactDeploySource extends DeploySource {
    private static final Logger LOGGER = Logger.getLogger(MavenArtifactDeploySource.class.getName());
    /**
     * The default type of artifact that this will look for.
     */
    public static final String DEFAULT_TYPE = "war";

    /**
     * The groupId to match or {@code null} to match any.
     */
    @CheckForNull
    private final String groupId;

    /**
     * The artifactId to match or {@code null} to match any.
     */
    @CheckForNull
    private final String artifactId;

    /**
     * The classifier to match or {@code null} to match the primary artifact.
     */
    @CheckForNull
    private final String classifier;

    /**
     * The type of artifact to match.
     */
    @NonNull
    private final String type;

    /**
     * Constructor used by stapler.
     *
     * @param groupId    the groupId to match or (@code null) to match any.
     * @param artifactId the artifactId to match or (@code null) to match any.
     * @param classifier the classifier to match or (@code null) to match the primary artifact.
     * @param type       the type of artifact to match.
     */
    @DataBoundConstructor
    public MavenArtifactDeploySource(@CheckForNull String groupId, @CheckForNull String artifactId,
                                     @CheckForNull String classifier, @CheckForNull String type) {
        this.groupId = fixWildcard(Util.fixEmptyAndTrim(groupId));
        this.artifactId = fixWildcard(Util.fixEmptyAndTrim(artifactId));
        this.classifier = Util.fixEmptyAndTrim(classifier);
        type = Util.fixEmptyAndTrim(type);
        this.type = type == null ? DEFAULT_TYPE : type;
    }

    /**
     * If a confused user enters {@code "*"} replace that by {@code null}
     *
     * @param v the string or {@code null}.
     * @return the same string or {@code null} if the string was either {@code null} or {@code "*"}.
     */
    @CheckForNull
    private static String fixWildcard(@CheckForNull String v) {
        return StringUtils.equals("*", v) ? null : v;
    }

    /**
     * Returns the artifactId to match or (@code null) to match any.
     *
     * @return the artifactId to match or (@code null) to match any.
     */
    @CheckForNull
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * Returns the classifier to match or (@code null) to match the primary artifact.
     *
     * @return the classifier to match or (@code null) to match the primary artifact.
     */
    @CheckForNull
    public String getClassifier() {
        return classifier;
    }

    /**
     * Returns the groupId to match or (@code null) to match any.
     *
     * @return the groupId to match or (@code null) to match any.
     */
    @CheckForNull
    public String getGroupId() {
        return groupId;
    }

    /**
     * Returns the type of artifact to match.
     *
     * @return the type of artifact to match.
     */
    @NonNull
    public String getType() {
        return type == null ? DEFAULT_TYPE : type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FilePath getApplicationFile(@NonNull FilePath workspace) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CheckForNull
    public File getApplicationFile(@NonNull Run run) {
        List<MavenBuild> builds;
        if (run instanceof MavenBuild) {
            builds = Arrays.asList((MavenBuild) run);
        } else if (run instanceof MavenModuleSetBuild) {
            builds = new ArrayList<MavenBuild>();
            for (List<MavenBuild> b : ((MavenModuleSetBuild) run).getModuleBuilds().values()) {
                builds.addAll(b);
            }
        } else {
            builds = Collections.emptyList();
        }
        List<Map.Entry<MavenArtifact, MavenArtifactRecord>> candidates =
                new ArrayList<Map.Entry<MavenArtifact, MavenArtifactRecord>>();
        for (MavenBuild build : builds) {
            List<MavenArtifactRecord> records = build.getActions(MavenArtifactRecord.class);
            if (records != null) {
                for (MavenArtifactRecord record : records) {
                    MavenArtifact mainArtifact = record.mainArtifact;
                    if (isCandidate(mainArtifact)) {
                        candidates.add(new SimpleEntry<MavenArtifact, MavenArtifactRecord>(
                                mainArtifact,
                                record));
                    }
                    if (isExactMatch(mainArtifact)) {
                        // return fast
                        try {
                            return mainArtifact.getFile(record.getBuild());
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                    for (MavenArtifact artifact : record.attachedArtifacts) {
                        if (isCandidate(artifact)) {
                            candidates.add(new SimpleEntry<MavenArtifact, MavenArtifactRecord>(
                                    artifact,
                                    record));
                        }
                        if (isExactMatch(artifact)) {
                            // return fast
                            try {
                                return artifact.getFile(record.getBuild());
                            } catch (IOException e) {
                                // ignore
                            }
                        }
                    }
                }
            }
        }
        if (candidates.size() == 1) {
            // we have exactly one candidate
            Map.Entry<MavenArtifact, MavenArtifactRecord> entry = candidates.get(0);
            MavenArtifact artifact = entry.getKey();
            try {
                return artifact.getFile(entry.getValue().getBuild());
            } catch (IOException e) {
                // ignore
            }
        }

        return null;
    }

    /**
     * Returns {@code true} if an only if the supplied artifact is a potential match.
     * A potential match is when at least one coordinate is unspecified and all the specified
     * coordinates agree.
     *
     * @param artifact the artifact.
     * @return {@code true} if an only if the supplied artifact is a potential match.
     */
    private boolean isCandidate(MavenArtifact artifact) {
        if (!StringUtils.equals(getType(), artifact.type)) {
            return false;
        }
        if (groupId != null && !StringUtils.equals(groupId, artifact.groupId)) {
            return false;
        }
        if (artifactId != null && !StringUtils.equals(artifactId, artifact.artifactId)) {
            return false;
        }
        return StringUtils.equals(Util.fixNull(classifier), Util.fixNull(artifact.classifier));
    }

    /**
     * Returns {@code true} if and only if the supplied artifact is an exact match.
     * An exact match occurs when none of the coordinates are unspecified and all coordinates
     * agree.
     *
     * @param artifact the artifact.
     * @return {@code true} if and only if the supplied artifact is an exact match.
     */
    private boolean isExactMatch(MavenArtifact artifact) {
        if (!StringUtils.equals(getType(), artifact.type)) {
            return false;
        }
        if (groupId == null || !StringUtils.equals(groupId, artifact.groupId)) {
            return false;
        }
        if (artifactId == null || !StringUtils.equals(artifactId, artifact.artifactId)) {
            return false;
        }
        return StringUtils.equals(Util.fixNull(classifier), Util.fixNull(artifact.classifier));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(groupId == null ? "*" : groupId);
        sb.append(':');
        sb.append(artifactId == null ? "*" : artifactId);
        if (classifier != null) {
            sb.append(':');
            sb.append(classifier);
        }
        sb.append(':');
        sb.append(getType());
        return sb.toString();
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

        MavenArtifactDeploySource that = (MavenArtifactDeploySource) o;

        if (artifactId != null ? !artifactId.equals(that.artifactId) : that.artifactId != null) {
            return false;
        }
        if (classifier != null ? !classifier.equals(that.classifier) : that.classifier != null) {
            return false;
        }
        if (groupId != null ? !groupId.equals(that.groupId) : that.groupId != null) {
            return false;
        }
        if (!type.equals(that.type)) {
            return false;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int result = groupId != null ? groupId.hashCode() : 0;
        result = 31 * result + (artifactId != null ? artifactId.hashCode() : 0);
        result = 31 * result + (classifier != null ? classifier.hashCode() : 0);
        result = 31 * result + type.hashCode();
        return result;
    }

    /**
     * Descriptor for {@link MavenArtifactDeploySource}
     */
    @Extension
    public static class DescriptorImpl extends DeploySourceDescriptor {

        private final WeakHashMap<Run, WeakReference<List<MavenBuild>>> buildCache = new WeakHashMap<Run,
                WeakReference<List<MavenBuild>>>();

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.MavenArtifactDeploySource_DisplayName();
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
        public boolean isApplicable(@CheckForNull Class<? extends AbstractProject> jobType) {
            return isMavenJob(jobType);
        }

        @SuppressWarnings("unused") // used by stapler
        public FormValidation doCheckGroupId(@QueryParameter final String value) throws IOException, ServletException {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.ok();
            }
            List<MavenBuild> builds = findMavenBuilds();
            if (builds.isEmpty()) {
                return FormValidation.ok();
            }
            for (MavenArtifact artifact : getAllArtifacts(builds)) {
                if (StringUtils.equals(artifact.groupId, value)) {
                    return FormValidation.ok();
                }
            }
            return FormValidation.warning("Could not find a .war artifact with group id '" + value + "'");
        }

        @SuppressWarnings("unused") // used by stapler
        public ComboBoxModel doFillGroupIdItems() {
            Set<String> result = new TreeSet<String>();
            for (MavenArtifact artifact : getAllArtifacts(findMavenBuilds())) {
                result.add(artifact.groupId);
            }
            return new ComboBoxModel(result);
        }

        @SuppressWarnings("unused") // used by stapler
        public FormValidation doCheckArtifactId(@QueryParameter final String groupId,
                                                @QueryParameter final String value,
                                                @QueryParameter final String classifier,
                                                @QueryParameter final String type)
                throws IOException, ServletException {
            List<MavenBuild> builds = findMavenBuilds();
            if (builds.isEmpty()) {
                return FormValidation.ok();
            }
            Set<String> artifactIds = new TreeSet<String>();
            Set<String> groupIds = new TreeSet<String>();
            Set<String> classifiers = new TreeSet<String>();
            for (MavenArtifact artifact : getAllArtifacts(builds)) {
                if (StringUtils.isBlank(value) || StringUtils.equals(value, artifact.artifactId)) {
                    artifactIds.add(artifact.artifactId);
                    if (StringUtils.isBlank(groupId) || StringUtils.equals(groupId, artifact.groupId)) {
                        groupIds.add(artifact.groupId);
                        classifiers.add(Util.fixNull(artifact.classifier));
                    }
                }
            }
            String defaultedType = StringUtils.isEmpty(type) ? DEFAULT_TYPE : type;
            if (artifactIds.isEmpty()) {
                if (StringUtils.isEmpty(value)
                        && StringUtils.isEmpty(groupId)
                        && StringUtils.isEmpty(classifier)
                        && StringUtils.isEmpty(type)) {
                    return FormValidation.ok();
                }
                if (StringUtils.isEmpty(groupId)) {
                    return FormValidation.warning(
                            "Could not find a ." + defaultedType + " artifact with artifact id '" + value + "'");
                }
                return FormValidation.warning(
                        "Could not find a ." + defaultedType + " artifact with id '" + groupId + ":" + value +
                                "'");
            }
            if (StringUtils.isEmpty(value)) {
                if (artifactIds.size() > 1 || groupIds.size() > 1) {
                    return FormValidation
                            .error("There are multiple ." + defaultedType
                                    + " artifacts, you must identify the one you want to deploy");
                }
            }
            assert artifactIds.size() == 1;
            if (StringUtils.isEmpty(groupId) && groupIds.size() > 1) {
                return FormValidation
                        .error("There are multiple ." + defaultedType
                                + " artifacts with this artifact id that differ only in their "
                                + "group id, you should specify the group id of the artifact you want");
            }
            if (classifiers.size() > 1) {
                if (StringUtils.isEmpty(classifier)) {
                    return FormValidation.warning(
                            "There are multiple ." + defaultedType
                                    + " artifacts with this artifact id that differ only in their classifier"
                                    + ".");
                }
                if (!classifiers.contains(classifier)) {
                    return FormValidation.warning(
                            "Could not find a ." + defaultedType + " artifact with id '" + groupId + ":" + value +
                                    ":" + classifier + "'");
                }
            }
            return FormValidation.ok();
        }

        @SuppressWarnings("unused") // used by stapler
        public ComboBoxModel doFillArtifactIdItems(@QueryParameter final String groupId) {
            Set<String> result = new TreeSet<String>();
            for (MavenArtifact artifact : getAllArtifacts(findMavenBuilds())) {
                if (StringUtils.isBlank(groupId) || StringUtils.equals(groupId, artifact.groupId)) {
                    result.add(artifact.artifactId);
                }
            }
            return new ComboBoxModel(result);
        }

        @SuppressWarnings("unused") // used by stapler
        public ComboBoxModel doFillClassifierItems(@QueryParameter final String groupId,
                                                   @QueryParameter final String artifactId) {
            Set<String> result = new TreeSet<String>();
            for (MavenArtifact artifact : getAllArtifacts(findMavenBuilds())) {
                if (StringUtils.isBlank(groupId) || StringUtils.equals(groupId, artifact.groupId)) {
                    if (StringUtils.isBlank(artifactId) || StringUtils.equals(artifactId, artifact.artifactId)) {
                        result.add(Util.fixNull(artifact.classifier));
                    }
                }
            }
            return new ComboBoxModel(result);
        }

        @SuppressWarnings("unused") // used by stapler
        public ComboBoxModel doFillTypeItems(@QueryParameter final String groupId,
                                             @QueryParameter final String artifactId,
                                             @QueryParameter final String classifier) {
            Set<String> result = new TreeSet<String>();
            for (MavenArtifact artifact : getAllArtifacts(findMavenBuilds())) {
                if (!"pom".equals(artifact.type)) {
                    if (StringUtils.isBlank(groupId) || StringUtils.equals(groupId, artifact.groupId)) {
                        if (StringUtils.isBlank(artifactId) || StringUtils.equals(artifactId, artifact.artifactId)) {
                            if (StringUtils.isBlank(classifier) || StringUtils
                                    .equals(classifier, artifact.classifier)) {
                                result.add(Util.fixNull(artifact.type));
                            }
                        }
                    }
                }
            }
            return new ComboBoxModel(result);
        }

        private List<MavenBuild> findMavenBuilds() {
            Run run = findRun();
            List<MavenBuild> builds;
            synchronized (buildCache) {
                WeakReference<List<MavenBuild>> reference = buildCache.get(run);
                if (reference != null) {
                    builds = reference.get();
                    if (builds != null) {
                        return builds;
                    }
                }
            }
            if (run instanceof MavenBuild) {
                builds = Arrays.asList((MavenBuild) run);
            } else if (run instanceof MavenModuleSetBuild) {
                builds = new ArrayList<MavenBuild>();
                for (List<MavenBuild> b : ((MavenModuleSetBuild) run).getModuleBuilds().values()) {
                    builds.addAll(b);
                }
            } else {
                builds = Collections.emptyList();
            }
            synchronized (buildCache) {
                buildCache.put(run, new WeakReference<List<MavenBuild>>(builds));
            }
            return builds;
        }

        private Collection<MavenArtifact> getWebAppArtifacts(Collection<MavenBuild> builds) {
            Collection<MavenArtifact> result = new ArrayList<MavenArtifact>();
            for (MavenBuild build : builds) {
                List<MavenArtifactRecord> records = build.getActions(MavenArtifactRecord.class);
                if (records != null) {
                    for (MavenArtifactRecord record : records) {
                        MavenArtifact mainArtifact = record.mainArtifact;
                        if (StringUtils.equals("war", mainArtifact.type)) {
                            result.add(mainArtifact);
                        }
                        for (MavenArtifact artifact : record.attachedArtifacts) {
                            if (StringUtils.equals("war", artifact.type)) {
                                result.add(artifact);
                            }
                        }
                    }
                }
            }
            return result;
        }

        private Collection<MavenArtifact> getAllArtifacts(Collection<MavenBuild> builds) {
            Collection<MavenArtifact> result = new ArrayList<MavenArtifact>();
            for (MavenBuild build : builds) {
                result.addAll(getAllArtifacts(build));
            }
            return result;
        }

        private Collection<MavenArtifact> getAllArtifacts(MavenBuild build) {
            Collection<MavenArtifact> result = new ArrayList<MavenArtifact>();
            List<MavenArtifactRecord> records = build.getActions(MavenArtifactRecord.class);
            if (records != null) {
                for (MavenArtifactRecord record : records) {
                    MavenArtifact mainArtifact = record.mainArtifact;
                    result.add(mainArtifact);
                    for (MavenArtifact artifact : record.attachedArtifacts) {
                        result.add(artifact);
                    }
                }
            }
            return result;
        }

        @Override
        public DeploySource newInstance() {
            return new MavenArtifactDeploySource(null, null, null, "war");
        }
    }

    private static class SimpleEntry<K, V> implements Map.Entry<K, V> {

        private final K key;
        private final V value;

        private SimpleEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }

        public V setValue(V value) {
            throw new UnsupportedOperationException();
        }
    }

}
