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
import com.cloudbees.plugins.deployer.resolvers.CapabilitiesResolver;
import com.cloudbees.plugins.deployer.sources.DeploySource;
import com.cloudbees.plugins.deployer.targets.DeployTarget;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.reporters.MavenAggregatedArtifactRecord;
import hudson.maven.reporters.MavenArtifact;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Run;
import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A column that shows the Deploy Now button.
 */
public class DeployNowColumn extends ListViewColumn {
    @DataBoundConstructor
    public DeployNowColumn() {
    }

    @SuppressWarnings("unused") // stapler
    public boolean isDeployPossible(Item item) {
        return DescriptorImpl.isDeployPossible(item);
    }

    @Extension
    public static class DescriptorImpl extends ListViewColumnDescriptor {

        private final Map<Run, Boolean> deployPossibleCache = new WeakHashMap<Run, Boolean>();

        private final Map<Run, Map<DeploySource, Boolean>> sourceHasNoFile =
                new WeakHashMap<Run, Map<DeploySource, Boolean>>();

        Boolean cache(Run run, DeploySource source) {
            synchronized (sourceHasNoFile) {
                Map<DeploySource, Boolean> map = sourceHasNoFile.get(run);
                return map != null ? map.get(source) : null;
            }
        }

        boolean cache(Run run, DeploySource source, boolean answer) {
            synchronized (sourceHasNoFile) {
                Map<DeploySource, Boolean> map = sourceHasNoFile.get(run);
                if (map == null) {
                    map = new LinkedHashMap<DeploySource, Boolean>();
                    sourceHasNoFile.put(run, map);
                }
                map.put(source, answer);
                return answer;
            }
        }

        Boolean cache(Run run) {
            synchronized (deployPossibleCache) {
                return deployPossibleCache.get(run);
            }
        }

        boolean cache(Run run, boolean answer) {
            synchronized (deployPossibleCache) {
                deployPossibleCache.put(run, answer);
            }
            return answer;
        }

        void decache(Run run) {
            synchronized (deployPossibleCache) {
                deployPossibleCache.remove(run);
            }
        }

        @Override
        public String getDisplayName() {
            return Messages.DeployNowColumn_DisplayName();
        }

        public static boolean isDeployPossible(Item item) {
            if (!(item instanceof AbstractProject)) {
                return false;
            }
            AbstractProject project = (AbstractProject) item;
            if (!CapabilitiesResolver.of(project).isInstantApplicable()) {
                return false;
            }
            if (!project.hasPermission(DeployNowRunAction.DEPLOY)) {
                return false;
            }
            Run lastDeployable = CapabilitiesResolver.getLastDeployableBuild(project);
            if (lastDeployable == null) {
                // quickest answer
                return false;
            }
            DeployNowJobProperty deployNow =
                    (DeployNowJobProperty) project.getProperty(DeployNowJobProperty.class);
            if (deployNow != null && !deployNow.getHosts().isEmpty() && hasArtifacts(lastDeployable)) {
                boolean checkedOne = false;
                final DescriptorImpl instance = instance();
                for (DeployHost<?, ?> host : deployNow.getHosts()) {
                    for (DeployTarget<?> target : host.getTargets()) {
                        final DeploySource s = target.getArtifact();
                        if (s != null) {
                            checkedOne = true;
                            if (Boolean.TRUE.equals(instance.cache(lastDeployable, s))
                                    || instance
                                    // TODO use variant which returns boolean
                                    .cache(lastDeployable, s, s.getApplicationFile(lastDeployable) == null)) {
                                return false;
                            }
                        }
                    }
                }
                if (checkedOne) {
                    return true;
                }
            }
            if (item instanceof MavenModuleSet) {
                MavenModuleSet maven = (MavenModuleSet) item;
                for (MavenModule m : maven.getModules()) {
                    if (isDeployPossible(m)) {
                        return true;
                    }
                }
            } else if (item instanceof MatrixProject) {
                MatrixProject matrix = (MatrixProject) item;
                for (MatrixConfiguration m : matrix.getActiveConfigurations()) {
                    if (isDeployPossible(m)) {
                        return true;
                    }
                }
            }
            return isDeployPossible(lastDeployable);
        }

        private static boolean hasArtifacts(Run run) {
            // some project types are "special"
            if (run instanceof MavenModuleSetBuild) {
                MavenAggregatedArtifactRecord mavenArtifacts = ((MavenModuleSetBuild) run).getMavenArtifacts();
                if (mavenArtifacts != null) {
                    for (MavenArtifactRecord r : mavenArtifacts.getModuleRecords()) {
                        if (r.mainArtifact != null && !"pom".equals(r.mainArtifact.type)) {
                            return true;
                        }
                        if (!r.attachedArtifacts.isEmpty()) {
                            return true;
                        }
                    }
                }
            }
            if (run instanceof MavenBuild) {
                MavenArtifactRecord r = ((MavenBuild) run).getMavenArtifacts();
                if (r != null) {
                    if (r.mainArtifact != null && !"pom".equals(r.mainArtifact.type)) {
                        return true;
                    }
                    if (!r.attachedArtifacts.isEmpty()) {
                        return true;
                    }
                }
            }
            return run.getHasArtifacts();
        }

        public static boolean isDeployPossible(Run run) {
            if (Hudson.getInstance().isQuietingDown() || Hudson.getInstance().isTerminating()) {
                return false;
            }
            if (run instanceof MavenModuleSetBuild) {
                Boolean cached = getCachedDeployPossible(run);
                return cached != null
                        ? cached
                        : setCachedDeployPossible(run, foundWar(((MavenModuleSetBuild) run).getMavenArtifacts()));
            }
            if (run instanceof MavenBuild) {
                Boolean cached = getCachedDeployPossible(run);
                return cached != null
                        ? cached
                        : setCachedDeployPossible(run, foundWar(((MavenBuild)run).getMavenArtifacts()));
            }
            if (run.getHasArtifacts()) {
                Boolean cached = getCachedDeployPossible(run);
                return cached != null
                        ? cached
                        : setCachedDeployPossible(run, foundWar(run.getArtifactsDir()));
            } else {
                removeCachedDeployPossible(run);
                return false;
            }
        }

        private static Boolean getCachedDeployPossible(Run lastSuccessfulBuild) {
            return instance().cache(lastSuccessfulBuild);
        }

        private static boolean setCachedDeployPossible(Run lastSuccessfulBuild, boolean deployPossible) {
            return instance().cache(lastSuccessfulBuild, deployPossible);
        }

        private static void removeCachedDeployPossible(Run lastSuccessfulBuild) {
            instance().decache(lastSuccessfulBuild);
        }

        @NonNull
        private static DescriptorImpl instance() {
            return ((DescriptorImpl) Hudson.getInstance().getDescriptorOrDie(DeployNowColumn.class));
        }

        private static boolean foundWar(MavenAggregatedArtifactRecord mavenArtifacts) {
            if (mavenArtifacts != null) {
                for (MavenArtifactRecord r : mavenArtifacts.getModuleRecords()) {
                    if (foundWar(r)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean foundWar(MavenArtifactRecord mavenArtifacts) {
            if (mavenArtifacts != null) {
                if (mavenArtifacts.mainArtifact != null && "war".equals(mavenArtifacts.mainArtifact.type)) {
                    return true;
                }
                for (MavenArtifact a : mavenArtifacts.attachedArtifacts) {
                    if ("war".equals(a.type)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean foundWar(File file) {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                // check depth last, as we want an answer as quick as possible
                for (File f : files) {
                    if (f.isFile() && f.getName().endsWith(".war")) {
                        return true;
                    }
                }
                for (File f : files) {
                    if (f.isDirectory() && foundWar(f)) {
                        return true;
                    }
                }

            } else if (!file.isFile()) {
                return file.getName().endsWith(".war");
            }
            return false;
        }

    }
}
