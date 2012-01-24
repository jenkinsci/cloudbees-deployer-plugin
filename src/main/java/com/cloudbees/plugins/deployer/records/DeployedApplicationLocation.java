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

package com.cloudbees.plugins.deployer.records;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.OverrideMustInvoke;
import edu.umd.cs.findbugs.annotations.When;

import java.io.Serializable;

/**
 * Represents information about an application that has been deployed to an URL.
 *
 * @since 4.0
 */
public abstract class DeployedApplicationLocation implements Serializable {

    /**
     * The URL that the application can be accessed at.
     */
    @NonNull
    private final String locationURL;

    /**
     * Constructs a new instance.
     *
     * @param locationURL the URL that the application can be accessed at.
     */
    protected DeployedApplicationLocation(@NonNull String locationURL) {
        locationURL.getClass(); // throw NPE if null
        this.locationURL = locationURL;
    }

    /**
     * Returns the URL that the application can be accessed at.
     *
     * @return the URL that the application can be accessed at.
     */
    @NonNull
    public String getLocationURL() {
        return locationURL;
    }

    /**
     * Returns the URL to the image.
     *
     * @param size The size specified. Must support "16x16", "24x24", and "32x32" at least.
     *             For forward compatibility, if you receive a size that's not supported,
     *             consider returning your biggest icon (and let the browser rescale.)
     * @return The URL is rendered as is in the img @src attribute, so it must contain
     *         the context path, etc.
     */
    @CheckForNull
    public abstract String getImageOf(@CheckForNull String size);

    /**
     * Returns the display name of the hosting service.
     *
     * @return the display name of the hosting service.
     */
    @CheckForNull
    public abstract String getDisplayName();

    /**
     * Returns the description of the deployment, e.g. deployment parameters etc.
     *
     * @return the description of the deployment, e.g. deployment parameters etc.
     */
    @CheckForNull
    public abstract String getDescription();

    /**
     * {@inheritDoc}
     */
    @Override
    @OverrideMustInvoke(When.ANYTIME)
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DeployedApplicationLocation that = (DeployedApplicationLocation) o;

        if (!locationURL.equals(that.locationURL)) {
            return false;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @OverrideMustInvoke(When.ANYTIME)
    public int hashCode() {
        return locationURL.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DeployedApplicationLocation");
        sb.append("{locationURL='").append(locationURL).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
