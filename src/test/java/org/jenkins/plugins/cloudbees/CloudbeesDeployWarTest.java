package org.jenkins.plugins.cloudbees;

import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.Cause;
import hudson.tasks.Maven;
import org.jvnet.hudson.test.ExtractResourceSCM;

/**
 * @author Olivier Lamy
 */
public class CloudbeesDeployWarTest extends AbstractCloudbeesDeployerPluginTest
{

    public void testWithMavenProject() throws Exception {
        MavenModuleSet m = createMavenProject( );
        Maven.MavenInstallation mavenInstallation = configureDefaultMaven();
        m.setMaven( mavenInstallation.getName() );
        CloudbeesAccount cloudbeesAccount = new CloudbeesAccount("olamy", "key", "so secret key");
        CloudbeesPublisher.DESCRIPTOR.setAccounts( cloudbeesAccount );
        m.setGoals("clean install");
        m.setScm(new ExtractResourceSCM(getClass().getResource( "test-project.zip" )));
        //m.getPublishers().add( new CloudbeesPublisher("olamy", "foo/beer", null) );
        //m.scheduleBuild2( 0, new Cause.UserCause() );
        MavenModuleSetBuild mmsb =  buildAndAssertSuccess(m);
    }
}
