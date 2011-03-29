/*
 * Copyright 2010-2011, CloudBees Inc.
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

import com.cloudbees.api.AccountInfo;
import com.cloudbees.api.AccountKeysResponse;
import com.cloudbees.api.ApplicationInfo;
import com.cloudbees.api.ApplicationListResponse;
import com.cloudbees.api.BeesClient;
import com.cloudbees.api.BeesClientException;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Olivier Lamy
 */
public class CloudbeesProjectProperty extends JobProperty<AbstractProject<?, ?>> {

    public final String accountName;

    @DataBoundConstructor
    public CloudbeesProjectProperty(String accountName) {
        if (accountName == null) {
            // revert to first one
            CloudbeesAccount[] accounts =  DESCRIPTOR.getAccounts();

            if( accounts.length > 0) {
                accountName = accounts[0].name;
            }
        }
        this.accountName = accountName;
    }


	@Extension
	public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

	public static final class DescriptorImpl extends JobPropertyDescriptor {

        private final CopyOnWriteList<CloudbeesAccount> accounts = new CopyOnWriteList<CloudbeesAccount>();

        public DescriptorImpl() {
            super(CloudbeesProjectProperty.class);
            load();
        }

        @Override
        public String getDisplayName()
        {
            // TODO i18n
            return "Cloudbess Accounts";
        }

		public boolean isApplicable(Class<? extends Job> jobType) {
			return AbstractProject.class.isAssignableFrom(jobType);
		}

		@Override
		public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData)
				throws FormException {
			CloudbeesProjectProperty cpp = req.bindParameters(
					CloudbeesProjectProperty.class, "cloudbeesaccount.");
			if (cpp.accountName == null) cpp = null;
			return cpp;
		}

		@Override
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
            System.out.println("ckeck secretKey:" + secretKey + ",with apiKey:"+apiKey);

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
    }


    private static final Logger LOGGER = Logger .getLogger( CloudbeesProjectProperty.class.getName() );
}
