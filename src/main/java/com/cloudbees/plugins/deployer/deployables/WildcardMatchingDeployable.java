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

package com.cloudbees.plugins.deployer.deployables;

import com.cloudbees.plugins.deployer.impl.run.RunTargetImpl;
import com.cloudbees.plugins.deployer.sources.WildcardPathDeploySource;
import org.apache.commons.lang.StringUtils;

import java.util.logging.Logger;

/**
 * @author stephenc
 * @since 31/01/2012 12:52
 */
@Deprecated
@SuppressWarnings("deprecation")
public class WildcardMatchingDeployable extends Deployable {
    private static final Logger LOGGER = Logger.getLogger(WildcardMatchingDeployable.class.getName());

    private final String filePattern;

    public WildcardMatchingDeployable(String applicationId, String filePattern, String applicationEnvironment,
                                      Setting[] applicationConfig, String apiEndPoint) {
        super(applicationId, applicationEnvironment, applicationConfig, apiEndPoint);
        this.filePattern = filePattern;
    }

    public String getFilePattern() {
        return StringUtils.isEmpty(filePattern) ? "**/*.war" : filePattern;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(filePattern).append(" -> ");
        sb.append(super.toString());
        return sb.toString();
    }

    @Override
    public RunTargetImpl toDeployTarget() {
        return new RunTargetImpl(getApiEndPoint(), getApplicationId(), getApplicationEnvironment(), null, toSettings(),
                new WildcardPathDeploySource(filePattern), false, null, null, null);
    }

}
