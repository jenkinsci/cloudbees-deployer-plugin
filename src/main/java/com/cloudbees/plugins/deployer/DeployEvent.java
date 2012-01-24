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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.AbstractBuild;
import hudson.model.Cause;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author stephenc
 * @since 06/06/2012 17:00
 */
public class DeployEvent implements Serializable {
    @NonNull
    private final AbstractBuild<?, ?> build;
    @NonNull
    private final List<Cause> causes;

    public DeployEvent(@NonNull AbstractBuild<?, ?> build, @NonNull List<Cause> causes) {
        build.getClass();
        causes.getClass();
        this.build = build;
        this.causes = Collections.unmodifiableList(new ArrayList<Cause>(causes));
    }

    @NonNull
    public AbstractBuild<?, ?> getBuild() {
        return build;
    }

    @NonNull
    public List<Cause> getCauses() {
        return causes;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DeployEvent");
        sb.append("{build=").append(build);
        sb.append(", causes=").append(causes);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DeployEvent that = (DeployEvent) o;

        if (!build.equals(that.build)) {
            return false;
        }
        if (!causes.equals(that.causes)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = build.hashCode();
        result = 31 * result + (causes != null ? causes.hashCode() : 0);
        return result;
    }
}
