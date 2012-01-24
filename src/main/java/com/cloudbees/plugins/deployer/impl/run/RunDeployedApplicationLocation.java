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

package com.cloudbees.plugins.deployer.impl.run;

import com.cloudbees.plugins.deployer.records.DeployedApplicationLocation;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang.StringUtils;

/**
 * @author stephenc
 * @since 17/12/2012 12:33
 */
public class RunDeployedApplicationLocation extends DeployedApplicationLocation {
    private final String applicationId;
    private final String applicationEnvironment;

    public RunDeployedApplicationLocation(@NonNull String applicationId, @NonNull String applicationEnvironment,
                                          @NonNull String appURL) {
        super(appURL);
        applicationId.getClass(); // throw NPE if null
        applicationEnvironment.getClass(); // throw NPE if null
        this.applicationId = applicationId;
        this.applicationEnvironment = applicationEnvironment;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getApplicationEnvironment() {
        return applicationEnvironment;
    }

    @Override
    public String getImageOf(@CheckForNull String size) {
        return "/plugin/cloudbees-deployer-plugin/images/" + (StringUtils.isBlank(size) ? "24x24" : size)
                + "/deployed-on-run.png";
    }

    @Override
    public String getDisplayName() {
        return Messages.RunHostImpl_DisplayName();
    }

    @Override
    public String getDescription() {
        return Messages.RunDeployedApplicationLocation_Description(applicationId, applicationEnvironment);
    }
}
