package org.jenkins.plugins.cloudbees;

import com.cloudbees.api.UploadProgress;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.util.IOException2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkins.plugins.cloudbees.util.FileFinder;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class CloudbeesDeployer {

    private final CloudbeesPublisher cloudbeesPublisher;

    public CloudbeesDeployer(CloudbeesPublisher cloudbeesPublisher) {
        this.cloudbeesPublisher = cloudbeesPublisher;
    }

    public boolean deploy(AbstractBuild<?, ?> build, Launcher launcher, final BuildListener listener)
            throws InterruptedException, IOException {

        CloudbeesAccount cloudbeesAccount = cloudbeesPublisher.getCloudbeesAccount();

        if (cloudbeesAccount == null) {
            listener.getLogger().println(Messages._CloudbeesPublisher_noAccount());
            return false;
        }

        listener.getLogger().println(Messages._CloudbeesPublisher_perform(cloudbeesPublisher.getCloudbeesAccount().name, cloudbeesPublisher.applicationId));

        CloudbeesApiHelper.CloudbeesApiRequest apiRequest =
                new CloudbeesApiHelper.CloudbeesApiRequest(CloudbeesApiHelper.CLOUDBEES_API_URL, cloudbeesAccount.apiKey,
                        cloudbeesAccount.secretKey);

        List<ArtifactFilePathSaveAction> artifactFilePathSaveActions = cloudbeesPublisher.retrieveArtifactFilePathSaveActions(build);

        if (artifactFilePathSaveActions.isEmpty() && StringUtils.isBlank(cloudbeesPublisher.filePattern)) {
            listener.getLogger().println(Messages._CloudbeesPublisher_noArtifacts(build.getProject().getName()));
            return true;
        }


        String warPath = null;
        findWarPath:
        for (ArtifactFilePathSaveAction artifactFilePathSaveAction : artifactFilePathSaveActions) {
            for (MavenArtifactWithFilePath artifactWithFilePath : artifactFilePathSaveAction.mavenArtifactWithFilePaths) {
                if (StringUtils.equals("war", artifactWithFilePath.type)) {
                    listener.getLogger().println(Messages.CloudbeesPublisher_WarPathFound(artifactWithFilePath));
                    warPath = artifactWithFilePath.filePath;
                    break findWarPath;
                }
            }
        }

        if (StringUtils.isBlank(warPath)) {
            if (StringUtils.isBlank(cloudbeesPublisher.filePattern)) {
                listener.getLogger().println(Messages._CloudbeesPublisher_noWarArtifacts());
                return false;
            } else {
                //search file in the workspace with the pattern
                FileFinder fileFinder = new FileFinder(cloudbeesPublisher.filePattern);
                List<String> fileNames = build.getWorkspace().act(fileFinder);
                listener.getLogger().println("found remote files : " + fileNames);
                if (fileNames.size() > 1) {
                    listener.getLogger().println(Messages.CloudbeesPublisher_ToManyFilesMatchingPattern());
                    return false;
                } else if (fileNames.size() == 0) {
                    listener.getLogger().println(Messages._CloudbeesPublisher_noArtifactsFound(cloudbeesPublisher.filePattern));
                    return false;
                }
                // so we use only the first found
                warPath = fileNames.get(0);
            }
        }


        File tmpArchive = File.createTempFile("jenkins", "temp-cloudbees-deploy");

        try {

            // handle remote slave case so copy war locally
            Node buildNode = Hudson.getInstance().getNode(build.getBuiltOnStr());
            FilePath filePath = new FilePath(tmpArchive);

            FilePath remoteWar = build.getWorkspace().child(warPath);

            remoteWar.copyTo(filePath);

            warPath = tmpArchive.getPath();

            listener.getLogger().println(Messages.CloudbeesPublisher_Deploying(cloudbeesPublisher.applicationId));

            String description = "Jenkins build " + build.getId();
            CloudbeesApiHelper.getBeesClient(apiRequest).applicationDeployWar(cloudbeesPublisher.applicationId, "environnement",
                    description, warPath, warPath, new ConsoleListenerUploadProgress(listener));
            CloudbeesDeployerAction cloudbeesDeployerAction = new CloudbeesDeployerAction(cloudbeesPublisher.applicationId);
            cloudbeesDeployerAction.setDescription(description);

            build.addAction(cloudbeesDeployerAction);
        } catch (Exception e) {
            listener.getLogger().println("issue during deploying war " + e.getMessage());
            throw new IOException2(e.getMessage(), e);
        } finally {
            FileUtils.deleteQuietly(tmpArchive);
        }

        return true;
    }

    private class ConsoleListenerUploadProgress implements UploadProgress {
        private final PrintStream console;
        private boolean uploadComplete = false;
        private long hashMarkCount = 0;

        public ConsoleListenerUploadProgress(BuildListener buildListener) {
            this.console = buildListener.getLogger();
        }

        // mostly a copy/paste from HashWriteProgress waiting for https://github.com/cloudbees/cloudbees-api-client/pull/5
        public void handleBytesWritten(long deltaCount,
                                       long totalWritten, long totalToSend) {
            if (uploadComplete)
                return;

            int totalMarks = (int)(totalWritten/(totalToSend/100f));
            while(hashMarkCount < totalMarks) {
                hashMarkCount++;
                if(hashMarkCount % 25 == 0) {
                    if(hashMarkCount < 100)
                        console.println(String.format("uploaded %d%%", hashMarkCount));
                    else {
                        //upload completed (or will very soon)
                        uploadComplete = true;
                        console.println("upload completed");
                        console.println("deploying application to server(s)...");
                    }
                } else console.print(".");
            }
        }
    }
}
