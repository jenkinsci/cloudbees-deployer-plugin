/*
 * Copyright 2010-2011, Olivier Lamy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkins.plugins.cloudbees;

import hudson.model.Action;

/**
 * Action which contains various informations regarding a deployment to @RUN
 * tru the cloudbees-deployer-plugin
 * @author Olivier Lamy
 * @since 1.5
 */
public class CloudbeesDeployerAction implements Action {

    private String applicationId;

    private String description;

    public CloudbeesDeployerAction(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getIconFileName()
    {
        return null;
    }

    public String getDisplayName()
    {
        return null;
    }

    public String getUrlName()
    {
        return null;
    }

    public String getApplicationId()
    {
        return applicationId;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription( String description )
    {
        this.description = description;
    }
}
