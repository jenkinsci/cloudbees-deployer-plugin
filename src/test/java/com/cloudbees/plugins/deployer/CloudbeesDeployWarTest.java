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
import com.cloudbees.plugins.deployer.impl.run.RunHostImpl;
import com.cloudbees.plugins.deployer.impl.run.RunTargetImpl;
import com.cloudbees.plugins.deployer.sources.MavenArtifactDeploySource;
import com.cloudbees.plugins.deployer.sources.WildcardPathDeploySource;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Olivier Lamy
 */
public class CloudbeesDeployWarTest
        extends AbstractCloudbeesDeployerPluginTest {

    public void testWithMaven2Project()
            throws Exception {
        MavenModuleSet m = createMavenProject();
        Maven.MavenInstallation mavenInstallation = configureDefaultMaven();
        m.setMaven(mavenInstallation.getName());

        m.setGoals("clean install");
        m.setScm(new ExtractResourceSCM(getClass().getResource("test-project.zip")));
        m.getPublishers().add(new DeployPublisher(Arrays.asList(new RunHostImpl("test@test.test", "test-account",
                Collections.singletonList(new RunTargetImpl(EndPoints.runAPI(), "test-app", null, null, null,
                        new MavenArtifactDeploySource(null, "translate-puzzle-webapp", null, null), false, null, null, null)))),false));
        MavenModuleSetBuild mmsb = buildAndAssertSuccess(m);
        assertOnFileItems(mmsb.getFullDisplayName());
    }

    public void testWithMaven3Project()
            throws Exception {
        MavenModuleSet m = createMavenProject();
        Maven.MavenInstallation mavenInstallation = configureMaven3();
        m.setMaven(mavenInstallation.getName());

        m.setGoals("clean install");
        m.setScm(new ExtractResourceSCM(getClass().getResource("test-project.zip")));
        m.getPublishers().add(new DeployPublisher(Arrays.asList(new RunHostImpl("test@test.test", "test-account",
                Collections.singletonList(new RunTargetImpl(EndPoints.runAPI(), "test-app", null, null, null,
                        new MavenArtifactDeploySource(null, "translate-puzzle-webapp", null, null), false, null, null, null)))), false));
        MavenModuleSetBuild mmsb = buildAndAssertSuccess(m);
        assertOnFileItems(mmsb.getFullDisplayName());
    }

    public void testFreestyleAnt() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new ExtractResourceSCM(getClass().getResource("test-project.zip")));
        String antName = configureDefaultAnt().getName();
        p.getBuildersList().add(new Ant("", antName, null, null, "vBAR=<xml/>\n"));
        p.getBuildersList().add(new DeployBuilder(Arrays.asList(new RunHostImpl("test@test.test", "test-account",
                Collections.singletonList(new RunTargetImpl(EndPoints.runAPI(), "test-app", null, null, null,
                        new WildcardPathDeploySource("**/translate-puzzle-webapp*.war"), false, null, null, null))))));
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
                assertOnArchive(fileItem.getInputStream());
            } else {
                System.out.println(" item " + fileItem);
            }

        }

    }

    public static void assertOnArchive(InputStream inputStream)
            throws IOException {
        List<String> fileNames = new ArrayList<String>();
        ZipInputStream zipInputStream = null;
        try {
            zipInputStream = new ZipInputStream(inputStream);
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                fileNames.add(zipEntry.getName());
                zipEntry = zipInputStream.getNextEntry();
            }
        } finally {
            IOUtils.closeQuietly(zipInputStream);
        }
        assertTrue(fileNames.contains("META-INF/maven/org.olamy.puzzle.translate/translate-puzzle-webapp/pom.xml"));
        assertTrue(fileNames.contains("WEB-INF/lib/javax.inject-1.jar"));
    }
}
