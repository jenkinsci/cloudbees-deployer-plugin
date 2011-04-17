/*
 * Copyright 2010-2011, CloudBees Inc.
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

import org.jenkins.plugins.cloudbees.util.CloudbeesServer;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Olivier Lamy
 */
public abstract class AbstractCloudbeesDeployerPluginTest  extends HudsonTestCase
{

    public CloudbeesServer cloudbeesServer = new CloudbeesServer();

    public CloudbeesAccount cloudbeesAccount;

    @Override
    protected void setUp()
        throws Exception
    {
        super.setUp();
        this.cloudbeesServer.startServer();
        cloudbeesAccount = new CloudbeesAccount( "olamy", "key", "so secret key" );
        CloudbeesPublisher.DESCRIPTOR.setAccounts( cloudbeesAccount );
        CloudbeesPublisher.DescriptorImpl.CLOUDBEES_API_URL = "http://localhost:" + cloudbeesServer.getPort();
    }

    @Override
    protected void tearDown()
        throws Exception
    {
        super.tearDown();
        this.cloudbeesServer.stopServer();
    }
}
