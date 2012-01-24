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

import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ResourceList;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.AbstractQueueTask;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.SubTask;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * A deploy now task.
 */
public class DeployNowTask extends AbstractQueueTask {

    private final AbstractBuild<?, ?> build;

    private final String name;
    private final DeployNowRunAction.Deployer deployer;

    private transient volatile long nextCheckForNode;

    public DeployNowTask(AbstractBuild<?, ?> build, DeployNowRunAction.Deployer deployer) {
        this.build = build;
        this.name = " → " + deployer.getDisplayName();
        this.deployer = deployer;
    }

    public boolean isBuildBlocked() {
        return getCauseOfBlockage() != null;
    }

    @Deprecated
    public String getWhyBlocked() {
        CauseOfBlockage causeOfBlockage = getCauseOfBlockage();
        return causeOfBlockage == null ? null : causeOfBlockage.getShortDescription();
    }

    public CauseOfBlockage getCauseOfBlockage() {
        if (System.currentTimeMillis() > nextCheckForNode) {
            nextCheckForNode = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60);
            Computer.threadPoolForRemoting.submit(new Runnable() {
                public void run() {
                    DeployNowSlave.ensurePresent();
                }
            });
        }
        return null;
    }

    public String getName() {
        return build.getDisplayName() + name;
    }

    public String getFullDisplayName() {
        return build.getFullDisplayName() + name;
    }

    public void checkAbortPermission() {
    }

    public boolean hasAbortPermission() {
        return false;
    }

    public String getUrl() {
        return build.getUrl() + "deploy-now/deploy";
    }

    public boolean isConcurrentBuild() {
        return false;
    }

    public ResourceList getResourceList() {
        return new ResourceList();
    }

    public String getDisplayName() {
        return build.getDisplayName() + name;
    }

    public Label getAssignedLabel() {
        return new LabelAtom(DeployNowSlave.NODE_LABEL_STRING);
    }

    public Node getLastBuiltOn() {
        return null;
    }

    public long getEstimatedDuration() {
        return -1;
    }

    public Queue.Executable createExecutable() throws IOException {
        return new ExecutableImpl();
    }

    public Object getSameNodeConstraint() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DeployNowTask that = (DeployNowTask) o;

        if (build != null ? !build.equals(that.build) : that.build != null) {
            return false;
        }
        if (deployer != null ? !deployer.equals(that.deployer) : that.deployer != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return build != null ? build.hashCode() : 0;
    }

    public DeployNowRunAction.Deployer getDeployer() {
        return deployer;
    }

    public AbstractBuild<?, ?> getBuild() {
        return build;
    }

    private class ExecutableImpl implements Queue.Executable {

        public SubTask getParent() {
            return DeployNowTask.this;
        }

        public void run() {
            DeployNowRunAction action = build.getAction(DeployNowRunAction.class);
            if (action == null) {
                action = new DeployNowRunAction(build);
            }
            action.run(deployer);
        }

        public long getEstimatedDuration() {
            return -1;
        }

    }
}
