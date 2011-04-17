package org.jenkins.plugins.cloudbees;

import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.tasks.Maven;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jvnet.hudson.test.ExtractResourceSCM;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Olivier Lamy
 */
public class CloudbeesDeployWarTest
    extends AbstractCloudbeesDeployerPluginTest
{

    public void testWithMavenProject()
        throws Exception
    {
        MavenModuleSet m = createMavenProject();
        Maven.MavenInstallation mavenInstallation = configureDefaultMaven();
        m.setMaven( mavenInstallation.getName() );
        CloudbeesAccount cloudbeesAccount = new CloudbeesAccount( "olamy", "key", "so secret key" );
        CloudbeesPublisher.DESCRIPTOR.setAccounts( cloudbeesAccount );
        CloudbeesPublisher.DescriptorImpl.CLOUDBEES_API_URL = "http://localhost:" + cloudbeesServer.getPort();
        m.setGoals( "clean install" );
        m.setScm( new ExtractResourceSCM( getClass().getResource( "test-project.zip" ) ) );
        m.getPublishers().add( new CloudbeesPublisher( "olamy", "foo/beer", null ) );
        MavenModuleSetBuild mmsb = buildAndAssertSuccess( m );
        for ( FileItem fileItem : cloudbeesServer.cloudbessServlet.items )
        {

            //archive check it's a war content

            //description check Jenkins BUILD_ID
            if ( fileItem.getFieldName().equals( "description" ) )
            {
                String description = fileItem.getString();
                // sample Jenkins build 2011-04-17_17-35-05
                assertTrue( description.contains( "Jenkins build" ) );
                String id = StringUtils.substringAfter( description, "Jenkins build" ).trim();
                // TODO assert it's a date time with a parsing !!
                System.out.println( "id : " + id );
            }
            else if ( fileItem.getFieldName().equals( "api_key" ) )
            {
                assertEquals( "key", fileItem.getString() );
            }
            else if ( fileItem.getFieldName().equals( "app_id" ) )
            {
                assertEquals( "foo/beer", fileItem.getString() );
            }
            else if ( fileItem.getFieldName().equals( "archive" ) )
            {
                assertOnArchive( fileItem.getInputStream() );
            }
            else
            {
                System.out.println( " item " + fileItem );
            }

        }

    }

    public void assertOnArchive( InputStream inputStream )
        throws IOException
    {
        List<String> fileNames = new ArrayList<String>();
        ZipInputStream zipInputStream = null;
        try
        {
            zipInputStream = new ZipInputStream( inputStream );
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while ( zipEntry != null )
            {
                fileNames.add( zipEntry.getName() );
                zipEntry = zipInputStream.getNextEntry();
            }
        }
        finally
        {
            IOUtils.closeQuietly( zipInputStream );
        }
        assertTrue( fileNames.contains( "META-INF/maven/org.olamy.puzzle.translate/translate-puzzle-webapp/pom.xml" ) );
        assertTrue( fileNames.contains( "WEB-INF/lib/javax.inject-1.jar" ) );
    }
}
