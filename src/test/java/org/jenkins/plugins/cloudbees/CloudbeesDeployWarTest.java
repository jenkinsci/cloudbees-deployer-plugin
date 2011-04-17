package org.jenkins.plugins.cloudbees;

import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.Cause;
import hudson.tasks.Maven;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang.StringUtils;
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
        CloudbeesPublisher.DescriptorImpl.CLOUDBEES_API_URL = "http://localhost:" + cloudbeesServer.getPort();
        m.setGoals("clean install");
        m.setScm(new ExtractResourceSCM(getClass().getResource( "test-project.zip" )));
        m.getPublishers().add( new CloudbeesPublisher("olamy", "foo/beer", null) );
        MavenModuleSetBuild mmsb =  buildAndAssertSuccess(m);
        for(FileItem fileItem : cloudbeesServer.cloudbessServlet.items) {

            //archive check it's a war content

            //description check Jenkins BUILD_ID
            if (fileItem.getFieldName().equals( "description" )) {
                String description = fileItem.getString();
                // sample Jenkins build 2011-04-17_17-35-05
                assertTrue( description.contains( "Jenkins build" ) );
                String id = StringUtils.substringAfter(description, "Jenkins build" ).trim();
                // TODO assert it's a date time with a parsing !!
                System.out.println("id : " + id);
            } else if (fileItem.getFieldName().equals( "api_key" )) {
                assertEquals( "key",  fileItem.getString() );
            } else if (fileItem.getFieldName().equals( "app_id" )) {
                assertEquals( "foo/beer",  fileItem.getString() );
            } else {
                System.out.println(" item " + fileItem );
            }

        }

    }
}
