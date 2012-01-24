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
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolLocationTranslator;

import java.io.IOException;

/**
 * Since we allow token expansion, we must provide a default Maven path -- in
 * case they are running on a master without any Mavens.
 * <p/>
 * We don't really need a working maven, so just return something besides null.
 *
 * @author Ryan Campbell
 */
@Extension(ordinal = -1)
public class MavenInstallationProvider extends ToolLocationTranslator {

    @Override
    public String getToolHome(Node node, ToolInstallation installation,
                              TaskListener log) throws IOException, InterruptedException {
        if (installation.getHome() == null && Executor.currentExecutor() != null
                && Executor.currentExecutor().getOwner().getNode() instanceof DeployNowSlave) {
            return "/notmaven";
        } else {
            return null;
        }
    }

}
