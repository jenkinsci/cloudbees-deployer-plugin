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

import com.cloudbees.EndPoints;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.cloudbees.AbstractCloudBeesAccount;
import com.cloudbees.plugins.credentials.cloudbees.AbstractCloudBeesUser;
import com.cloudbees.plugins.credentials.cloudbees.CloudBeesAccount;
import hudson.ExtensionList;
import hudson.util.Secret;
import org.jenkins.plugins.cloudbees.util.CloudbeesServer;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.Collections;
import java.util.List;

/**
 * @author Olivier Lamy
 */
public abstract class AbstractCloudbeesDeployerPluginTest extends HudsonTestCase {

    public CloudbeesServer cloudbeesServer = new CloudbeesServer();


    @Override
    protected void setUp()
            throws Exception {
        super.setUp();
        this.cloudbeesServer.startServer();
        ExtensionList<SystemCredentialsProvider> list =
                hudson.getExtensionList(SystemCredentialsProvider.class);
        assertTrue("have system credentials provider", list.size() > 0);
        SystemCredentialsProvider provider = list.iterator().next();

        provider.getCredentials().add(new AbstractCloudBeesUser() {
            public String getName() {
                return "test@test.test";
            }

            public String getDisplayName() {
                return "Test Test";
            }

            public Secret getPassword() {
                return Secret.fromString("Not So Secret");
            }

            public String getAPIKey() {
                return "Testing121212Testing";
            }

            public Secret getAPISecret() {
                return Secret.fromString("So Very Secret");
            }

            public String getUsername() {
                return "testy";
            }

            public String getUID() {
                return "123456789";
            }

            public List<CloudBeesAccount> getAccounts() {
                return Collections.<CloudBeesAccount>singletonList(new AbstractCloudBeesAccount() {
                    public String getName() {
                        return "test-account";
                    }

                    public String getDisplayName() {
                        return "Test Account";
                    }
                });
            }
        });
        EndPoints.hookOverride("com.cloudbees.EndPoints.runAPI", "http://localhost:" + cloudbeesServer.getPort());
        assertEquals("http://localhost:" + cloudbeesServer.getPort(), EndPoints.runAPI());
    }

    @Override
    protected void tearDown()
            throws Exception {
        EndPoints.hookReset();
        this.cloudbeesServer.stopServer();
        super.tearDown();
    }
}
