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
        xStream.alias("ApplicationListResponse", ApplicationListResponse.class);
        xStream.alias("ApplicationInfo", ApplicationInfo.class);
        xStream.alias("ApplicationCheckSumsResponse", ApplicationCheckSumsResponse.class);
        xStream.alias("ApplicationDeployArchiveResponse", ApplicationDeployArchiveResponse.class);
    }

    public static String applicationListResponse() {
        ApplicationListResponse applicationListResponse = new ApplicationListResponse();
        applicationListResponse.getApplications()
                .add(new ApplicationInfo("foo1", "nice application1", new Date(), "running",
                        new String[]{"http://foo1.bar"}));
        applicationListResponse.getApplications()
                .add(new ApplicationInfo("foo2", "nice application2", new Date(), "sucks",
                        new String[]{"http://foo2.bar"}));
        return xStream.toXML(applicationListResponse);
    }

    public static String applicationCheckSumsResponse() {
        ApplicationCheckSumsResponse applicationCheckSumsResponse = new ApplicationCheckSumsResponse();
        applicationCheckSumsResponse.setCheckSums(new HashMap<String, Long>(0));
        return xStream.toXML(applicationCheckSumsResponse);
    }

    public static String applicationDeployArchiveResponse() {
        ApplicationDeployArchiveResponse response = new ApplicationDeployArchiveResponse("id", "url");
        return xStream.toXML(response);
    }

    public static String sayHelloResponse() {
        SayHelloResponse sayHelloResponse = new SayHelloResponse();
        return xStream.toXML(sayHelloResponse);
    }

}
