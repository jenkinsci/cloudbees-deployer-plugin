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

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.CauseOfBlockage;
import hudson.slaves.EphemeralNode;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.ClockDifference;
import org.kohsuke.stapler.export.Exported;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * A special node for executing {@link DeployNowTask}s on the master, even when the master does not have any executors.
 */
public class DeployNowSlave extends Slave implements EphemeralNode {

    public static final String NODE_LABEL_STRING = "CloudBees Deploy Now Node";

    public DeployNowSlave() throws Descriptor.FormException, IOException {
        super(Messages.DeployNowSlave_NodeName(), Messages.DeployNowSlave_NodeDescription(), "", 1,
                Mode.EXCLUSIVE, null, null, DeployNowComputer.RETENTION_STRATEGY,
                Collections.<NodeProperty<?>>singletonList(new NodePropertyImpl()));
    }

    @Exported
    public Set<LabelAtom> getAssignedLabels() {
        Set<LabelAtom> r = new TreeSet<LabelAtom>(super.getAssignedLabels());
        r.add(new LabelAtom(NODE_LABEL_STRING));
        return Collections.unmodifiableSet(r);
    }

    @Override
    public Launcher createLauncher(TaskListener listener) {
        return new Launcher.LocalLauncher(listener).decorateFor(this);
    }

    @Override
    public Computer createComputer() {
        return new DeployNowComputer(this);
    }

    @Override
    public FilePath getWorkspaceFor(TopLevelItem item) {
        return Hudson.getInstance().getWorkspaceFor(item);
    }

    @Override
    public FilePath getRootPath() {
        return Hudson.getInstance().getRootPath();
    }

    @Override
    public ClockDifference getClockDifference() throws IOException, InterruptedException {
        return ClockDifference.ZERO;
    }

    public static synchronized DeployNowSlave ensurePresent() {
        return DeployNowComputer.RETENTION_STRATEGY.ensurePresent();
    }

    public Node asNode() {
        return this;
    }

    @Extension
    public static class DescriptorImpl extends SlaveDescriptor {

        @Override
        public boolean isInstantiable() {
            return false;
        }

        @Override
        public String getDisplayName() {
            return null;
        }
    }

    public static class NodePropertyImpl extends NodeProperty<DeployNowSlave> {
        @Override
        public CauseOfBlockage canTake(Queue.BuildableItem item) {
            if (item.task instanceof DeployNowTask) {
                return null;
            }
            return CauseOfBlockage.fromMessage(Messages._DeployNowSlave_OnlyAcceptsDeployNowTasks());
        }

        @Extension
        public static class DescriptorImpl extends NodePropertyDescriptor {

            @Override
            public String getDisplayName() {
                return null;
            }
        }
    }
}
