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

import com.cloudbees.plugins.deployer.deployables.Deployable;
import com.cloudbees.plugins.deployer.impl.run.RunHostImpl;
import com.cloudbees.plugins.deployer.impl.run.RunTargetImpl;
import hudson.model.AbstractDescribableImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A deployment configuration.
 */
@Deprecated
public class DeployConfiguration extends AbstractDescribableImpl<DeployConfiguration> {

    private final String user;

    private final String account;

    private transient List<Deployable> deployables;

    @Deprecated
    @SuppressWarnings("deprecation")
    public DeployConfiguration(String user, String account,
                               List<Deployable> deployables) {
        this.user = user;
        this.account = account;
        this.deployables =
                new ArrayList<Deployable>(deployables == null ? Collections.<Deployable>emptyList() : deployables);
    }

    public String getUser() {
        return user;
    }

    public String getAccount() {
        return account;
    }

    @SuppressWarnings("deprecation")
    public List<Deployable> getDeployables() {
        return deployables;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DeployConfiguration that = (DeployConfiguration) o;

        if (account != null ? !account.equals(that.account) : that.account != null) {
            return false;
        }
        if (deployables != null ? !deployables.equals(that.deployables) : that.deployables != null) {
            return false;
        }
        if (user != null ? !user.equals(that.user) : that.user != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = user != null ? user.hashCode() : 0;
        result = 31 * result + (account != null ? account.hashCode() : 0);
        return result;
    }

    public RunHostImpl toDeploySet() {
        return new RunHostImpl(user, account, toDeployTargets());
    }

    private List<RunTargetImpl> toDeployTargets() {
        List<RunTargetImpl> targets = new ArrayList<RunTargetImpl>(deployables.size());
        for (Deployable deployable : deployables) {
            targets.add(deployable.toDeployTarget());
        }
        return targets;
    }

}
