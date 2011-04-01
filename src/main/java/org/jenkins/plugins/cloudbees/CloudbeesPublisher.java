/*
 * Copyright 2010-2011, CloudBees Inc., Olivier Lamy
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

import com.cloudbees.api.ApplicationInfo;
import com.cloudbees.api.ApplicationListResponse;
import com.cloudbees.api.BeesClientException;
import com.cloudbees.api.UploadProgress;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.reporters.MavenAbstractArtifactRecord;
import hudson.maven.reporters.MavenArtifact;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import hudson.util.IOException2;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Olivier Lamy
 */
public class CloudbeesPublisher extends Notifier {

    public final String accountName;

    public final String applicationId;

    @DataBoundConstructor
    public CloudbeesPublisher(String accountName, String applicationId) throws Exception {
        if (accountName == null) {
            // revert to first one
            CloudbeesAccount[] accounts =  DESCRIPTOR.getAccounts();

            if( accounts.length > 0) {
                accountName = accounts[0].name;
            }
        }
        this.accountName = accountName;

        this.applicationId = applicationId;
    }

    public CloudbeesAccount getCloudbeesAccount() {
        CloudbeesAccount[] accounts =  DESCRIPTOR.getAccounts();
        if (accountName == null && accounts.length > 0) {
            // return default
            return accounts[0];
        }
		for (CloudbeesAccount account : accounts) {
			if (account.name.equals(accountName))
				return account;
		}
		return null;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }


    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
        //TODO i18n
        listener.getLogger().println("CloudbeesPublisher :: perform " + this.getCloudbeesAccount().name + "::" + this.applicationId);

        CloudbeesAccount cloudbeesAccount = this.getCloudbeesAccount();


        CloudbeesApiHelper.CloudbeesApiRequest apiRequest =
                new CloudbeesApiHelper.CloudbeesApiRequest( CloudbeesApiHelper.CLOUDBEES_API_URL, cloudbeesAccount.apiKey, cloudbeesAccount.secretKey );

        List<ArtifactFilePathSaveAction> artifactFilePathSaveActions =  build.getActions(ArtifactFilePathSaveAction.class);

        if (artifactFilePathSaveActions.isEmpty()) {
            listener.getLogger().println(" not artifacts has been saved are you sure your build produced some ? ");
            return false;
        }

        String warPath=null;

        for (ArtifactFilePathSaveAction artifactFilePathSaveAction : artifactFilePathSaveActions)
        {
            listener.getLogger().println("artifacts " + artifactFilePathSaveAction.mavenArtifactWithFilePaths);
            for (MavenArtifactWithFilePath artifactWithFilePath : artifactFilePathSaveAction.mavenArtifactWithFilePaths ) {
                if (StringUtils.equals("war",artifactWithFilePath.type)) {
                    listener.getLogger().println("artifactWithFilePath"+artifactWithFilePath.filePath);
                    warPath = artifactWithFilePath.filePath;
                }
            }
        }

        if (StringUtils.isBlank(warPath)) {
            listener.getLogger().println(" not war artifact has been found are you sure your build produced some ? ");
            return false;
        }

        File tmpArchive = File.createTempFile( "jenkins", "temp-cloudbees-deploy" );

        try {

            // handle remote slave case so copy war locally
            Node buildNode =  Hudson.getInstance().getNode( build.getBuiltOnStr() );
            FilePath filePath = new FilePath( tmpArchive );

            FilePath remoteWar = build.getWorkspace().child( warPath );

            remoteWar.copyTo( filePath );

            warPath = tmpArchive.getPath();

            listener.getLogger().println(" deploying archive to cloudbees application " + applicationId);

            // TODO replace description with jenkins BUILD_ID ?

            CloudbeesApiHelper.getBeesClient(apiRequest).applicationDeployWar(applicationId, "environnement", "description", warPath,
            warPath, new UploadProgress(){
                        public void handleBytesWritten(long deltaCount, long totalWritten, long totalToSend) {
                            listener.getLogger().println(" upload : " + deltaCount/1024 + " ko, status " + totalWritten/1014 + " ko/" + totalToSend/1024 + " ko");
                        }
                    });
        } catch (Exception e) {
            listener.getLogger().println("issue during deploying war " + e.getMessage());
            throw new IOException2(e.getMessage(),e);
        } finally {
            FileUtils.deleteQuietly( tmpArchive );
        }

        return true;
    }


	@Extension
	public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private final CopyOnWriteList<CloudbeesAccount> accounts = new CopyOnWriteList<CloudbeesAccount>();

        public DescriptorImpl() {
            super(CloudbeesPublisher.class);
            load();
        }

        @Override
        public String getDisplayName()
        {
            // TODO i18n
            return "Cloudbess Deployment";
        }

        @Override
		public Publisher newInstance(StaplerRequest req, JSONObject formData) {
			CloudbeesPublisher cpp = req.bindParameters(
					CloudbeesPublisher.class, "cloudbeesaccount.");
			if (cpp.accountName == null) cpp = null;
			return cpp;
		}

		public boolean configure(StaplerRequest req, JSONObject formData) {
            List<CloudbeesAccount> accountList = req.bindParametersToList( CloudbeesAccount.class, "cloudbeesaccount." );
			accounts.replaceBy( accountList );
			save();
			return true;
		}

		/**
		 *
		 */
		public FormValidation doNameCheck(@QueryParameter final String name)
				throws IOException, ServletException {
			if (StringUtils.isBlank( name )) {
                // TODO i18n
                return FormValidation.error("name cannot be empty");
            }
            return FormValidation.ok();
		}

		/**
		 *
		 */
		public FormValidation doApiKeyCheck(@QueryParameter final String apiKey)
				throws IOException, ServletException {
			if (StringUtils.isBlank( apiKey )) {
                // TODO i18n
                return FormValidation.error("apiKey cannot be empty");
            }
            return FormValidation.ok();
		}

		public FormValidation doSecretKeyCheck(StaplerRequest request)
				throws IOException, ServletException {
            String secretKey = Util.fixEmpty(request.getParameter("secretKey"));
			if (StringUtils.isBlank( secretKey )) {
                // TODO i18n
                return FormValidation.error("secretKey cannot be empty");
            }
            // check valid account
            String apiKey = Util.fixEmpty(request.getParameter("apiKey"));
            if (StringUtils.isBlank( apiKey )) {
                // TODO i18n
                return FormValidation.error("apiKey cannot be empty");
            }

            CloudbeesApiHelper.CloudbeesApiRequest apiRequest =
                new CloudbeesApiHelper.CloudbeesApiRequest( CloudbeesApiHelper.CLOUDBEES_API_URL, apiKey, secretKey );

            try
            {
                CloudbeesApiHelper.ping( apiRequest );
            } catch ( BeesClientException e ) {
                if (e.getError() == null)
                {
                    LOGGER.log(Level.SEVERE, "Error during calling cloudbees api", e);
                    return FormValidation.error("Unknown error check server logs");
                } else {
                    return FormValidation.error(e.getError().getMessage());
                }
            }
            catch ( Exception e )
            {
                LOGGER.log(Level.SEVERE, "Error during calling cloudbees api", e);
                return FormValidation.error("Unknown error check server logs");
            }
            return FormValidation.ok();
		}

        public void setAccounts(CloudbeesAccount cloudbeesAccount) {
            accounts.add( cloudbeesAccount );
        }

        public CloudbeesAccount[] getAccounts() {
            return accounts.toArray( new CloudbeesAccount[0] );
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
          // check if type of FreeStyleProject.class or MavenModuleSet.class
          return true;
        }

		public FormValidation doApplicationIdCheck(@QueryParameter final String applicationId, @QueryParameter final String cloudbeesAccountName)
				throws IOException, ServletException {
            try {

                if (StringUtils.isBlank( applicationId )) {
                    // TODO i18n
                    return FormValidation.error("applicationId cannot be empty");
                }

                CloudbeesAccount cloudbeesAccount = getCloudbeesAccount( cloudbeesAccountName );
                ApplicationListResponse applicationListResponse =
                        CloudbeesApiHelper.applicationsList(new CloudbeesApiHelper.CloudbeesApiRequest(CloudbeesApiHelper.CLOUDBEES_API_URL,cloudbeesAccount ));
                List<ApplicationInfo> applicationInfos = applicationListResponse.getApplications();

                List<String> applicationIds = new ArrayList<String>(applicationInfos.size());

                AutoCompletionCandidates candidates = new AutoCompletionCandidates();
                for (ApplicationInfo applicationInfo : applicationInfos) {
                    if (StringUtils.equals( applicationInfo.getId(), applicationId )) {
                        return FormValidation.ok();
                    }
                    applicationIds.add( applicationInfo.getId() );
                }
                StringBuilder sb = new StringBuilder(  );

                for (String appId : applicationIds) {
                    sb.append( appId + " " );
                }

                return FormValidation.error( "possible applicationIds are " + sb.toString() );
            } catch ( Exception e ) {
              return FormValidation.error( e, "error during check applicationId " + e.getMessage() );
            }
		}

        // TODO fix those try to find a way to pass cloudbeesAccountName in autoCompleteUrl
        public AutoCompletionCandidates doAutoCompleteApplications(StaplerRequest staplerRequest )
            throws Exception
        {

            Enumeration enumeration = staplerRequest.getParameterNames();
            while ( enumeration.hasMoreElements() )
            {
                System.out.println("name " + (String) enumeration.nextElement());
            }

            String value = staplerRequest.getParameter( "value" );
            String cloudbeesAccountName = staplerRequest.getParameter( "cloudbeesAccountName" );
            System.out.println( "in doAutoCompleteApplications value:"+value+",cloudbeesAccountName"+cloudbeesAccountName);
            CloudbeesAccount cloudbeesAccount = getCloudbeesAccount( cloudbeesAccountName );

            ApplicationListResponse applicationListResponse =
                    CloudbeesApiHelper.applicationsList(new CloudbeesApiHelper.CloudbeesApiRequest(CloudbeesApiHelper.CLOUDBEES_API_URL,cloudbeesAccount ));
            List<ApplicationInfo> applicationInfos = applicationListResponse.getApplications();
            System.out.println( "found " + applicationInfos.size() + " applications" );

            AutoCompletionCandidates candidates = new AutoCompletionCandidates();
            for (ApplicationInfo applicationInfo : applicationInfos) {
                if (StringUtils.startsWith( applicationInfo.getId(), value )) {
                    System.out.println("found candidate " + applicationInfo.getId());
                    candidates.add( applicationInfo.getId() );//applicationInfo.getTitle(),
                }
            }

            return candidates;
        }

        public static CloudbeesAccount getCloudbeesAccount(String cloudbeesAccountName)
        {
            CloudbeesAccount[] accounts =  DESCRIPTOR.getAccounts();
            if (cloudbeesAccountName == null && accounts.length > 0) {
                // return default
                return accounts[0];
            }
            for (CloudbeesAccount account : accounts) {
                if (account.name.equals(cloudbeesAccountName))
                    return account;
            }
            return null;
        }

    }


    private static final Logger LOGGER = Logger .getLogger( CloudbeesPublisher.class.getName() );
}
