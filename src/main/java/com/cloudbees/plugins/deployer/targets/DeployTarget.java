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
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionPoint;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

/**
 * @author stephenc
 * @since 12/12/2012 14:30
 */
public abstract class DeployTarget<T extends DeployTarget<T>> extends AbstractDescribableImpl<T>
        implements ExtensionPoint, Serializable {

    @CheckForNull
    private final DeploySource artifact;

    protected DeployTarget(@CheckForNull DeploySource artifact) {
        this.artifact = artifact;
    }

    @CheckForNull
    public DeploySource getArtifact() {
        return artifact;
    }

    public abstract String getDisplayName();

    public boolean isValid(Run<?, ?> run) {
        if (isComplete() && artifact != null) {
            File artifactFile = artifact.getApplicationFile(run);
            return !(artifactFile == null || !isArtifactFileValid(artifactFile));
            // TODO delete artifactFile
        }
        return false;
    }

    protected abstract boolean isArtifactFileValid(File file);

    protected abstract boolean isComplete();

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DeployTarget{");
        sb.append("artifact=").append(artifact);
        sb.append('}');
        return sb.toString();
    }

    public static String expandAllMacros(AbstractBuild<?, ?> context, TaskListener listener,
                                         String stringPerhapsWithMacro) throws
            MacroEvaluationException, IOException, InterruptedException {
        if (StringUtils.isBlank(stringPerhapsWithMacro)) {
            return stringPerhapsWithMacro;
        }
        int index = stringPerhapsWithMacro.indexOf("${");
        if (index == -1) {
            return stringPerhapsWithMacro;
        }
        return TokenMacro.expandAll(context, listener, stringPerhapsWithMacro);
    }
}
