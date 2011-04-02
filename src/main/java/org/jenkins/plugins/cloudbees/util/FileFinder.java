/*
 * Copyright 2010-2011, CloudBees Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkins.plugins.cloudbees.util;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Olivier Lamy
 */
public class FileFinder implements FilePath.FileCallable<List<String>> {

    private final String pattern;

    public FileFinder(final String pattern) {
        this.pattern = pattern;

    }

    public List<String> invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
        return find(workspace);
    }

    public List<String> find(final File workspace)  {
        try {
            FileSet fileSet = new FileSet();
            Project antProject = new Project();
            fileSet.setProject(antProject);
            fileSet.setDir(workspace);
            fileSet.setIncludes(pattern);

            String[] files = fileSet.getDirectoryScanner(antProject).getIncludedFiles();
            return files == null ? Collections.<String>emptyList() : Arrays.asList(files);
        }
        catch (BuildException exception) {
            return Collections.emptyList();
        }
    }
}
