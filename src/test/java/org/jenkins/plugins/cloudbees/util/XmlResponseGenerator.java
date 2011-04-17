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
package org.jenkins.plugins.cloudbees.util;

import com.cloudbees.api.ApplicationCheckSumsResponse;
import com.cloudbees.api.ApplicationDeployArchiveResponse;
import com.cloudbees.api.ApplicationInfo;
import com.cloudbees.api.ApplicationListResponse;
import com.cloudbees.api.SayHelloResponse;
import com.thoughtworks.xstream.XStream;

import java.util.Date;
import java.util.HashMap;

/**
 * @author Olivier Lamy
 */
public class XmlResponseGenerator {

    static XStream xStream = new XStream();
    static {
        xStream.alias("ApplicationListResponse" , ApplicationListResponse.class);
        xStream.alias("ApplicationInfo" , ApplicationInfo.class);
        xStream.alias("ApplicationCheckSumsResponse", ApplicationCheckSumsResponse.class);
        xStream.alias("ApplicationDeployArchiveResponse", ApplicationDeployArchiveResponse.class);
    }

    public static String applicationListResponse() {
        ApplicationListResponse applicationListResponse = new ApplicationListResponse();
        applicationListResponse.getApplications().add(new ApplicationInfo("foo1", "nice application1", new Date(), "running", new String[]{"http://foo1.bar"}));
        applicationListResponse.getApplications().add(new ApplicationInfo("foo2", "nice application2", new Date(), "sucks", new String[]{"http://foo2.bar"}));
        return xStream.toXML(applicationListResponse);
    }

    public static String applicationCheckSumsResponse() {
        ApplicationCheckSumsResponse applicationCheckSumsResponse = new ApplicationCheckSumsResponse();
        applicationCheckSumsResponse.setCheckSums(new HashMap<String,Long>(0));
        return xStream.toXML(applicationCheckSumsResponse);
    }

    public static String applicationDeployArchiveResponse() {
        ApplicationDeployArchiveResponse response = new ApplicationDeployArchiveResponse("id", "url");
        return xStream.toXML(response);
    }

    public static String sayHelloResponse() {
        SayHelloResponse sayHelloResponse = new SayHelloResponse();
        return xStream.toXML( sayHelloResponse );
    }

}
