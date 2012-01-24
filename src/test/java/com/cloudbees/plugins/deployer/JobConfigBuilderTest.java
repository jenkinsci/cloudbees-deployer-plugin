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


import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.Ant;
import hudson.tasks.Maven;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.IOUtils;
import org.jvnet.hudson.test.ExtractResourceSCM;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Olivier Lamy
 * @author Ryan Campbell
 */
public class JobConfigBuilderTest
        extends AbstractCloudbeesDeployerPluginTest {

    public void testWithMavenProject()
            throws Exception {
        MavenModuleSet m = createMavenProject();
        Maven.MavenInstallation mavenInstallation = configureDefaultMaven();
        m.setMaven(mavenInstallation.getName());

        m.setGoals("clean install");
        m.setScm(new ExtractResourceSCM(getClass().getResource("test-project.zip")));
        
        JobConfigBuilder config = new JobConfigBuilder (
                "test-account", "test-app")
            .user("test@test.test")
            .mavenArtifact(null, null, null, "war");
        config.configure(m);
        MavenModuleSetBuild mmsb = buildAndAssertSuccess(m);
        assertOnFileItems(mmsb.getFullDisplayName());
    }

    public void testFreestyleAnt() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new ExtractResourceSCM(getClass().getResource("test-project.zip")));
        String antName = configureDefaultAnt().getName();
        p.getBuildersList().add(new Ant("", antName, null, null, "vBAR=<xml/>\n"));
        
        JobConfigBuilder config = new JobConfigBuilder("test-account", "test-app");
        config.user("test@test.test").filePattern("**/translate-puzzle-webapp*.war");
        config.configure(p);
        
        FreeStyleBuild build = buildAndAssertSuccess(p);
        assertOnFileItems(build.getFullDisplayName());
    }

    public void assertOnFileItems(String buildDescription)
            throws IOException {

        for (FileItem fileItem : cloudbeesServer.cloudbessServlet.items) {

            //archive check it's a war content

            //description check Jenkins BUILD_ID
            if (fileItem.getFieldName().equals("description")) {
                String description = fileItem.getString();
                assertEquals(buildDescription, description);
            } else if (fileItem.getFieldName().equals("api_key")) {
                assertEquals("Testing121212Testing", fileItem.getString());
            } else if (fileItem.getFieldName().equals("app_id")) {
                assertEquals("test-account/test-app", fileItem.getString());
            } else if (fileItem.getFieldName().equals("archive")) {
                CloudbeesDeployWarTest.assertOnArchive(fileItem.getInputStream());
            } else {
                System.out.println(" item " + fileItem);
            }

        }

    }

}
