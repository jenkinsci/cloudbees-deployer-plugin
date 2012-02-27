package org.jenkins.plugins.cloudbees;

import com.cloudbees.api.UploadProgress;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ConsoleNote;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.tasks._maven.MavenErrorNote;
import hudson.util.IOException2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.jenkins.plugins.cloudbees.util.FileFinder;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

public class CloudbeesDeployer {

    public final String accountName;

    public final String applicationId;

    public final String filePattern;
    
    public final CloudbeesAccount cloudbeesAccount;
    
    public CloudbeesDeployer(CloudbeesPublisher cloudbeesPublisher) {
        this.accountName = cloudbeesPublisher.accountName;
        this.applicationId = cloudbeesPublisher.applicationId;
        this.filePattern = cloudbeesPublisher.filePattern;
        this.cloudbeesAccount = cloudbeesPublisher.getCloudbeesAccount();
    }

    public boolean deploy(AbstractBuild<?, ?> build, Launcher launcher, final BuildListener listener)
            throws InterruptedException, IOException {

        if (cloudbeesAccount == null) {
            listener.error(Messages.CloudbeesPublisher_noAccount());
            return false;
        }

        listener.getLogger().println(Messages._CloudbeesPublisher_perform(cloudbeesAccount.name, applicationId));

        List<ArtifactFilePathSaveAction> artifactFilePathSaveActions = retrieveArtifactFilePathSaveActions(build);

        if (artifactFilePathSaveActions.isEmpty() && StringUtils.isBlank(filePattern)) {
            listener.getLogger().println(Messages._CloudbeesPublisher_noArtifacts(build.getProject().getName()));
            return true;
        }


        String warPath = null;
        Set<String> candidates = new HashSet<String>();
        for (ArtifactFilePathSaveAction artifactFilePathSaveAction : artifactFilePathSaveActions) {
            for (MavenArtifactWithFilePath artifactWithFilePath : artifactFilePathSaveAction.mavenArtifactWithFilePaths) {
                if (StringUtils.equals("war", artifactWithFilePath.type)) {
                    candidates.add(artifactWithFilePath.filePath);
                }
            }
        }

        if (candidates.size() > 1) {
            if (StringUtils.isBlank(filePattern)) {
                listener.error(Messages.CloudbeesDeployer_AmbiguousMavenWarArtifact());
                return false;
            }
            Iterator<String> it= candidates.iterator();
            while (it.hasNext()) {
                if (SelectorUtils.match(filePattern, it.next())) continue;
                it.remove();
            }

            if (candidates.size() > 1) {
                listener.error(Messages.CloudbeesDeployer_StillAmbiguousMavenWarArtifact());
                return false;
            }

            if (candidates.size() == 0) {
                listener.error(Messages.CloudbeesDeployer_NoWarArtifactToMatchPattern());
                return false;
            }
        }

        if (candidates.size() == 1) {
            warPath = candidates.iterator().next();
        }

        if (StringUtils.isBlank(warPath)) {
            if (StringUtils.isBlank(filePattern)) {
                listener.error(Messages.CloudbeesPublisher_noWarArtifacts());
                return false;
            } else {
                //search file in the workspace with the pattern
                FileFinder fileFinder = new FileFinder(filePattern);
                List<String> fileNames = build.getWorkspace().act(fileFinder);
                listener.getLogger().println("found remote files : " + fileNames);
                if (fileNames.size() > 1) {
                    listener.error(Messages.CloudbeesPublisher_ToManyFilesMatchingPattern());
                    return false;
                } else if (fileNames.size() == 0) {
                    listener.error(Messages.CloudbeesPublisher_noArtifactsFound(filePattern));
                    return false;
                }
                // so we use only the first found
                warPath = fileNames.get(0);
            }
        }

        listener.getLogger().println(Messages.CloudbeesPublisher_WarPathFound(warPath));
        doDeploy(build, listener, warPath);

        return true;
    }

    private void doDeploy(AbstractBuild<?, ?> build, BuildListener listener, String warPath) throws IOException {
        CloudbeesApiHelper.CloudbeesApiRequest apiRequest =
                new CloudbeesApiHelper.CloudbeesApiRequest(CloudbeesApiHelper.CLOUDBEES_API_URL, cloudbeesAccount.apiKey,
                        cloudbeesAccount.secretKey);

        File tmpArchive = File.createTempFile("jenkins", "temp-cloudbees-deploy");

        try {

            // handle remote slave case so copy war locally
            Node buildNode = Hudson.getInstance().getNode(build.getBuiltOnStr());
            FilePath filePath = new FilePath(tmpArchive);

            FilePath remoteWar = build.getWorkspace().child(warPath);

            // TODO why not run all this as a Callable so that this occur where the war is hosted ?
            remoteWar.copyTo(filePath);

            warPath = tmpArchive.getPath();

            listener.getLogger().println(Messages.CloudbeesPublisher_Deploying(applicationId));

            String description = "Jenkins build " + build.getId();
            CloudbeesApiHelper.getBeesClient(apiRequest).applicationDeployWar(applicationId, "environnement",
                    description, warPath, warPath, new ConsoleListenerUploadProgress(listener));
            CloudbeesDeployerAction cloudbeesDeployerAction = new CloudbeesDeployerAction(applicationId);
            cloudbeesDeployerAction.setDescription(description);

            build.addAction(cloudbeesDeployerAction);
        } catch (Exception e) {
            listener.getLogger().println("issue during deploying war " + e.getMessage());
            throw new IOException2(e.getMessage(), e);
        } finally {
            FileUtils.deleteQuietly(tmpArchive);
        }
    }

    private List<ArtifactFilePathSaveAction> retrieveArtifactFilePathSaveActions(AbstractBuild<?, ?> build) {
        List<ArtifactFilePathSaveAction> artifactFilePathSaveActions = new ArrayList<ArtifactFilePathSaveAction>();
        List<ArtifactFilePathSaveAction> actions = build.getActions(ArtifactFilePathSaveAction.class);
        if (actions != null) artifactFilePathSaveActions.addAll(actions);

        if (build instanceof MavenModuleSetBuild) {
            for (List<MavenBuild> mavenBuilds : ((MavenModuleSetBuild) build).getModuleBuilds().values()) {
                for (MavenBuild mavenBuild : mavenBuilds) {
                    actions = mavenBuild.getActions(ArtifactFilePathSaveAction.class);
                    if (actions != null) artifactFilePathSaveActions.addAll(actions);
                }
            }
        }
        return artifactFilePathSaveActions;
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
