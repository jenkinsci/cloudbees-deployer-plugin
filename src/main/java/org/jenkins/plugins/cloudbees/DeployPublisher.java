package org.jenkins.plugins.cloudbees;

import com.cloudbees.api.BeesClient;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: olamy
 * Date: 29/03/11
 * Time: 00:43
 * To change this template use File | Settings | File Templates.
 */
public class DeployPublisher extends Recorder {

    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

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
