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

import com.cloudbees.plugins.deployer.impl.run.RunDeployedApplicationLocation;
import com.cloudbees.plugins.deployer.records.DeployedApplicationFingerprintFacet;
import hudson.model.Fingerprint;
import jenkins.model.FingerprintFacet;

import java.io.ObjectStreamException;

/**
 * @author stephenc
 * @since 21/02/2012 15:50
 * @deprecated use {@link DeployedApplicationFingerprintFacet}
 */
@Deprecated
public class DeployFingerprintFacet extends FingerprintFacet {

    private final String applicationId;

    private final String applicationEnvironment;

    private final String url;

    /**
     * @param fingerprint            {@link hudson.model.Fingerprint} object to which this facet is going to be added
     *                               to.
     * @param timestamp              Timestamp when the use happened.
     * @param applicationId          The application id that the file was deployed to.
     * @param applicationEnvironment The application environment that the file was deployed with.
     * @param url                    The url that the file was deployed on.
     */
    public DeployFingerprintFacet(Fingerprint fingerprint, long timestamp, String applicationId,
                                  String applicationEnvironment, String url) {
        super(fingerprint, timestamp);
        this.applicationId = applicationId;
        this.applicationEnvironment = applicationEnvironment;
        this.url = url;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getApplicationEnvironment() {
        return applicationEnvironment;
    }

    public String getUrl() {
        return url;
    }

    @SuppressWarnings("deprecation")
    protected Object readResolve() throws ObjectStreamException {
        return new DeployedApplicationFingerprintFacet<RunDeployedApplicationLocation>(
                getFingerprint(),
                getTimestamp(),
                new RunDeployedApplicationLocation(applicationId, applicationEnvironment, url));
    }

}
