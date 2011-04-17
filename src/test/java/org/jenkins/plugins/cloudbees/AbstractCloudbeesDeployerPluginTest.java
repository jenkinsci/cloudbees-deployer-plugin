package org.jenkins.plugins.cloudbees;

import org.jenkins.plugins.cloudbees.util.CloudbeesServer;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Olivier Lamy
 */
public abstract class AbstractCloudbeesDeployerPluginTest  extends HudsonTestCase
{

    public CloudbeesServer cloudbeesServer = new CloudbeesServer();

    @Override
    protected void setUp()
        throws Exception
    {
        super.setUp();
        this.cloudbeesServer.startServer();
    }

    @Override
    protected void tearDown()
        throws Exception
    {
        super.tearDown();
        this.cloudbeesServer.stopServer();
    }
}
