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

import com.cloudbees.api.BeesClient;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;

import java.io.IOException;

/**
 * @author Olivier Lamy
 */
public class DeployPublisher extends Recorder {

    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        listener.getLogger().println(" cloudbees deploy publisher start ");
        String apiUrl = "https://api.cloudbees.com";
        String apiKey = ""; //from CloudBees account
        String secret = ""; //from CloudBees account
        BeesClient client = new BeesClient(apiUrl, apiKey, secret, "xml", "1.0");

        //client.tailLog("", "", listener.getLogger());
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
}
