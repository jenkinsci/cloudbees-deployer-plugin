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

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.plugins.deployedoncolumn.DeployedOnAction;

import java.io.Serializable;

/**
 * @author stephenc
 * @since 17/12/2012 12:24
 */
public class DeployedApplicationAction<L extends DeployedApplicationLocation>
        implements DeployedOnAction, Serializable {

    @NonNull
    private final L location;

    public DeployedApplicationAction(@NonNull L location) {
        location.getClass(); // throw NPE if null
        this.location = location;
    }

    @NonNull
    public L getLocation() {
        return location;
    }

    @NonNull
    public String getIconFileName() {
        return getImageOf("48x48");
    }

    public String getDisplayName() {
        return location.getDisplayName();
    }

    @NonNull
    public String getUrlName() {
        return location.getLocationURL();
    }

    public String getImageOf(String size) {
        return location.getImageOf(size);
    }

    public String getDescription() {
        return location.getDescription();
    }
}
