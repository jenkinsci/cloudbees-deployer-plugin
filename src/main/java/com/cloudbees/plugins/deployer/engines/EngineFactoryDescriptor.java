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

package com.cloudbees.plugins.deployer.engines;

import com.cloudbees.plugins.deployer.hosts.DeployHost;
import com.cloudbees.plugins.deployer.targets.DeployTarget;
import hudson.model.Descriptor;

/**
 * @author stephenc
 * @since 06/12/2012 13:44
 */
public abstract class EngineFactoryDescriptor<S extends DeployHost<S, T>, T extends DeployTarget<T>>
        extends Descriptor<EngineFactory<S, T>> {
    public abstract boolean isApplicable(Class<? extends DeployHost> aClass);

    public abstract EngineFactory<S, T> newFactory(S configuration);
}
