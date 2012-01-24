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

import com.cloudbees.EndPoints;
import com.cloudbees.plugins.deployer.impl.run.RunTargetImpl;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 * Something that can be deployed to RUN@cloud.
 */
@Deprecated
public abstract class Deployable extends AbstractDescribableImpl<Deployable>
        implements ExtensionPoint, Serializable {

    /**
     * The application id.
     */
    private final String applicationId;

    /**
     * The application environment.
     */
    private final String applicationEnvironment;

    /**
     * The application configuration.
     */
    private final Setting[] applicationConfig;

    /**
     * The RUN API end-point for the server holding the application
     */
    private /*final*/ String apiEndPoint;

    protected Deployable(String applicationId, String applicationEnvironment, Setting[] applicationConfig,
                         String apiEndPoint) {
        this.applicationId = applicationId;
        this.applicationEnvironment = applicationEnvironment;
        this.applicationConfig = applicationConfig == null ? new Setting[0] : applicationConfig;
        this.apiEndPoint = StringUtils.isBlank(apiEndPoint) ? EndPoints.runAPI() : apiEndPoint;
    }

    /**
     * Gets the application id that this deployable should be deployed to.
     *
     * @return the application id.
     */
    public String getApplicationId() {
        return applicationId;
    }

    /**
     * Gets the application environment that the application should be deployed as.
     *
     * @return the application environment to deploy the application as, or {@code null} to use the default.
     */
    public String getApplicationEnvironment() {
        return applicationEnvironment;
    }

    /**
     * Gets the application configuration.
     *
     * @return a copy of the application configuration.
     */
    public Setting[] getApplicationConfig() {
        return applicationConfig == null ? new Setting[0] : applicationConfig.clone();
    }

    public String getApiEndPoint() {
        if (apiEndPoint == null) {
            apiEndPoint = EndPoints.runAPI();
        }
        return apiEndPoint;
    }

    protected Object readResolve() throws ObjectStreamException {
        if (apiEndPoint == null) {
            apiEndPoint = EndPoints.runAPI();
        }
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Deployable that = (Deployable) o;

        if (getApplicationEnvironment() != null
                ? !getApplicationEnvironment().equals(that.getApplicationEnvironment())
                : that.getApplicationEnvironment() != null) {
            return false;
        }
        if (getApplicationId() != null
                ? !getApplicationId().equals(that.getApplicationId())
                : that.getApplicationId() != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = getApplicationId() != null ? getApplicationId().hashCode() : 0;
        result = 31 * result + (getApplicationEnvironment() != null ? getApplicationEnvironment().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(applicationId);
        if (!StringUtils.isEmpty(applicationEnvironment)) {
            sb.append(":").append(applicationEnvironment);
        }
        return sb.toString();
    }

    public abstract RunTargetImpl toDeployTarget();

    protected RunTargetImpl.Setting[] toSettings() {
        if (applicationConfig == null) {
            return null;
        }
        RunTargetImpl.Setting[] result = new RunTargetImpl.Setting[applicationConfig.length];
        for (int i = 0; i < applicationConfig.length; i++) {
            result[i] = applicationConfig[i] == null
                    ? null
                    : new RunTargetImpl.Setting(applicationConfig[i].getKey(), applicationConfig[i].getValue());
        }
        return result;
    }

    @Deprecated
    public static class Setting implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String key;

        private final String value;

        @DataBoundConstructor
        public Setting(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }
    }
}
