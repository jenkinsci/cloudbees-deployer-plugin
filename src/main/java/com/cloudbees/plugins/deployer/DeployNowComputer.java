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

import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import hudson.util.Futures;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.logging.LogRecord;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

/**
 * The {@link Computer} for a {@link DeployNowSlave}.
 */
public class DeployNowComputer extends SlaveComputer {

    public static final DeployNowRetentionStrategy RETENTION_STRATEGY = new DeployNowRetentionStrategy();

    private volatile boolean offline = false;

    public DeployNowComputer(DeployNowSlave node) {
        super(node);
    }

    @Override
    public boolean isOffline() {
        return offline;
    }

    @Override
    public Charset getDefaultCharset() {
        return Charset.defaultCharset();
    }

    @Override
    public List<LogRecord> getLogRecords() throws IOException, InterruptedException {
        return Hudson.logRecords;
    }

    @Override
    public HttpResponse doDoDelete() throws IOException {
        throw HttpResponses.forwardToView(this, "index");
    }

    @Override
    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        throw HttpResponses.forwardToView(this, "index");
    }

    @Override
    public void doLaunchSlaveAgent(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        rsp.sendError(SC_NOT_FOUND);
    }

    @Override
    protected Future<?> _connect(boolean forceReconnect) {
        return Futures.precomputed(null);
    }

    @Override
    public boolean isConnecting() {
        return false;
    }

    @Override
    public RetentionStrategy getRetentionStrategy() {
        return RETENTION_STRATEGY;
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        checkLater();
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        checkLater();
    }

    @Override
    public Map<String, Object> getMonitorData() {
        Computer computer = Hudson.getInstance().toComputer();
        return computer == null ? super.getMonitorData() : computer.getMonitorData();
    }

    private void checkLater() {
        Computer.threadPoolForRemoting.submit(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // ignore
                }
                RETENTION_STRATEGY.check(DeployNowComputer.this);
            }
        });
    }

    public static class DeployNowRetentionStrategy extends RetentionStrategy<DeployNowComputer> {
        @Override
        public synchronized long check(final DeployNowComputer c) {
            if (!c.isIdle()) {
                return 1;
            }
            for (Queue.Item t : Hudson.getInstance().getQueue().getItems()) {
                if (t.task instanceof DeployNowTask) {
                    return 1;
                }
            }
            if (!c.offline) {
                c.offline = true;
                try {
                    Hudson.getInstance().removeNode(c.getNode());
                } catch (IOException e) {
                    // ignore
                }
            }
            return 1;
        }

        public synchronized DeployNowSlave ensurePresent() {
            for (Node n : Hudson.getInstance().getNodes()) {
                if (n instanceof DeployNowSlave) {
                    DeployNowSlave deployNowSlave = (DeployNowSlave) n;
                    DeployNowComputer computer = (DeployNowComputer) deployNowSlave.getComputer();
                    if (computer != null && computer.offline) {
                        // if the remove thread has not run yet, prevent it from trampling us
                        computer.offline = false;
                    }
                    if (Hudson.getInstance().getNodes().contains(deployNowSlave)) {
                        // need final check as remove thread may have trampled us while we were searching initially
                        return deployNowSlave;
                    }
                }
            }
            try {
                DeployNowSlave n = new DeployNowSlave();
                Hudson.getInstance().addNode(n);
                return n;
            } catch (IOException e) {
                return null;
            } catch (Descriptor.FormException e) {
                return null;
            }

        }
    }

}
