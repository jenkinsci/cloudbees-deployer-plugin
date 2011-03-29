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

import com.cloudbees.api.ApplicationListResponse;
import com.cloudbees.api.BeesClient;

/**
 * @author Olivier Lamy
 */
public class CloudbeesApiHelper
{

    // to display api call in System.out
    public static boolean verbose = Boolean.getBoolean( "CloudbeesApiHelper.verbose" );

    // configurable ?
    public static String CLOUDBEES_API_URL = "https://api.cloudbees.com/api";

    public static ApplicationListResponse applicationsList(CloudbeesApiRequest cloudbeesApiRequest)
        throws Exception
    {
        return getBeesClient(cloudbeesApiRequest).applicationList();
    }

    /**
     * use the sayHello remote api to simulate a ping (will validate authz)
     * @param cloudbeesApiRequest
     * @throws Exception
     */
    public static void ping(CloudbeesApiRequest cloudbeesApiRequest)
        throws Exception {
       getBeesClient(cloudbeesApiRequest).sayHello( "hey" );
    }


    private static BeesClient getBeesClient(CloudbeesApiRequest cloudbeesApiRequest) {
        BeesClient client =
            new BeesClient(cloudbeesApiRequest.url, cloudbeesApiRequest.apiKey, cloudbeesApiRequest.secretKey, "xml", "1.0");
        client.setVerbose( CloudbeesApiHelper.verbose );
        return client;
    }

    protected static class CloudbeesApiRequest{
        private final String url;
        private final String apiKey;
        private final String secretKey;

        protected CloudbeesApiRequest(String url, String apiKey, String secretKey ) {
            this.url = url;
            this.apiKey = apiKey;
            this.secretKey = secretKey;
        }

        protected CloudbeesApiRequest(String url, CloudbeesAccount cloudbeesAccount ) {
            this.url = url;
            this.apiKey = cloudbeesAccount.apiKey;
            this.secretKey = cloudbeesAccount.secretKey;
        }
}

}
