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

import com.cloudbees.plugins.deployer.engines.Engine;
import com.cloudbees.plugins.deployer.hosts.DeployHost;
import com.cloudbees.plugins.deployer.hosts.DeployHostsContext;
import com.cloudbees.plugins.deployer.resolvers.CapabilitiesResolver;
import com.cloudbees.plugins.deployer.sources.DeploySourceOrigin;
import com.cloudbees.plugins.deployer.targets.DeployTarget;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.console.ConsoleLogFilter;
import hudson.console.ConsoleNote;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.Cause;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.RunAction;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.model.TransientBuildActionFactory;
import hudson.model.TransientProjectActionFactory;
import hudson.model.listeners.RunListener;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.tasks.BuildWrapper;
import hudson.util.FlushProofOutputStream;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.jelly.XMLOutput;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.servlet.ServletException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static com.cloudbees.plugins.deployer.DeployNowColumn.DescriptorImpl.isDeployPossible;

/**
 * An action for deploying a build's artifacts now.
 */
@ExportedBean
public class DeployNowRunAction implements RunAction {

    private Run<?, ?> owner;

    /**
     * If the build is in progress, remember {@link Deployer} that's running it.
     * This field is not persisted.
     */
    private volatile transient Deployer deployer;

    public static final PermissionGroup DEPLOY_NOW =
            new PermissionGroup(DeployNowRunAction.class, Messages._DeployNowRunAction_PermissionGroup());

    public static final Permission CONFIGURE = AbstractProject.CONFIGURE;

    public static final Permission DEPLOY = new Permission(DEPLOY_NOW, "Deploy", null, CONFIGURE,
            PermissionScope.ITEM);

    public static final Permission OWN_AUTH = new Permission(DEPLOY_NOW, "UserCredentials", null, DEPLOY,
            PermissionScope.ITEM);

    public static final Permission JOB_AUTH = new Permission(DEPLOY_NOW, "JobCredentials", null, CONFIGURE,
            PermissionScope.ITEM);

    public DeployNowRunAction() {
        this(null);
    }

    public DeployNowRunAction(Run<?, ?> owner) {
        this.owner = owner;
    }

    /**
     * Called by stapler to create the {@link com.cloudbees.plugins.deployer.hosts.DeployHostsContext}.
     *
     * @return the context.
     * @since 4.0
     */
    @SuppressWarnings("unused") // by stapler
    public DeployHostsContext<DeployNowRunAction> createHostsContext() {
        List<? extends DeployHost<?, ?>> hosts = null;
        if (owner != null) {
            DeployNowJobProperty property = owner.getParent().getProperty(DeployNowJobProperty.class);
            if (property != null) {
                hosts = property.getHosts();
                hosts = DeployHost.updateDefaults(owner, Collections.singleton(DeploySourceOrigin.RUN), hosts);
            }
        }
        if (hosts == null) {
            hosts = DeployHost.createDefaults(owner, Collections.singleton(DeploySourceOrigin.RUN));
        }
        return new DeployHostsContext<DeployNowRunAction>(this,
                hosts == null ? Collections.<DeployHost<?, ?>>emptyList() : hosts,
                owner == null ? null : owner.getParent(),
                Collections.singleton(DeploySourceOrigin.RUN),
                false,
                true);
    }

    @Exported(name = "oneClickDeployPossible", visibility = 2)
    public boolean isOneClickDeployPossible() {
        return isDeployPossible(owner);
    }

    @Exported(name = "oneClickDeployReady", visibility = 2)
    public boolean isOneClickDeploy() {
        if (owner != null) {
            DeployNowJobProperty property = owner.getParent().getProperty(DeployNowJobProperty.class);
            if (property != null) {
                return property.isOneClickDeploy();
            }
        }
        return true;
    }

    @Exported(name = "oneClickDeployValid", visibility = 2)
    public boolean isOneClickDeployValid() {
        if (owner != null && owner.getParent().hasPermission(DEPLOY)) {
            DeployNowJobProperty property = owner.getParent().getProperty(DeployNowJobProperty.class);
            if (property != null) {
                if (property.isOneClickDeploy()) {
                    List<? extends DeployHost<?, ?>> sets = property.getHosts();
                    if (owner.getParent().hasPermission(OWN_AUTH) && DeployHost
                            .isValid(sets, owner, Hudson.getAuthentication())) {
                        return true;
                    }
                    if (owner.getParent().hasPermission(JOB_AUTH) && DeployHost.isValid(sets, owner, ACL.SYSTEM)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isSaveConfigForced() {
        if (owner != null) {
            DeployNowJobProperty property = owner.getParent().getProperty(DeployNowJobProperty.class);
            return property == null;
        }
        return false;
    }

    public HttpResponse doIndex(StaplerRequest req) {
        if (!isDeployPossible(owner)) {
            return HttpResponses.notFound();
        }
        return HttpResponses.forwardToView(this, "configure");
    }

    public HttpResponse doDeploy(StaplerRequest req) throws ServletException {
        if ("POST".equalsIgnoreCase(req.getMethod())
                && owner.getParent().hasPermission(DEPLOY)
                && hasSubmittedForm(req)) {
            JSONObject json = req.getSubmittedForm();
            final List<DeployHost<?, ?>> sets = (List) req.bindJSONToList(DeployHost.class, json.get("hosts"));
            boolean saveConfig = json.optBoolean("saveConfig");
            boolean oneClickDeploy = json.optBoolean("oneClickDeploy");
            if (owner.getParent().hasPermission(CONFIGURE) && (oneClickDeploy || saveConfig)) {
                AbstractProject<?, ?> parent = (AbstractProject<?, ?>) owner.getParent();
                DeployNowJobProperty property = parent.getProperty(DeployNowJobProperty.class);
                try {
                    if (property == null) {
                        property = new DeployNowJobProperty(oneClickDeploy, sets);
                        parent.addProperty(property);
                    } else {
                        property.setOneClickDeploy(oneClickDeploy);
                        if (saveConfig) {
                            property.setHosts(sets);
                        }
                        parent.save();
                    }
                } catch (IOException e) {
                    // ignore
                }
            }
            if (!(owner.getParent().hasPermission(OWN_AUTH) && DeployHost
                    .isValid(sets, owner, Hudson.getAuthentication()))
                    && !(owner.getParent().hasPermission(JOB_AUTH) && DeployHost.isValid(sets, owner, ACL.SYSTEM))) {
                return HttpResponses.forwardToPreviousPage();
            }
            if (deployer == null) {
                getLogFile().delete();
                try {
                    getLogFile().createNewFile();
                } catch (IOException e) {
                    // ignore
                }
            }

            Hudson.getInstance().getQueue().schedule(
                    new DeployNowTask((AbstractBuild) owner, new Deployer(sets,
                            Arrays.<Cause>asList(new Cause.UserCause(), new DeployNowCause()),
                            Jenkins.getAuthentication())), 0);
        }
        return HttpResponses.forwardToView(this, "_deploy");
    }

    /**
     * Hack to help determine if {@link org.kohsuke.stapler.StaplerRequest#getSubmittedForm()} will bomb out
     *
     * @param req the request to check.
     * @return {@code true} if likely that {@link org.kohsuke.stapler.StaplerRequest#getSubmittedForm()} will return
     *         something.
     */
    private static boolean hasSubmittedForm(StaplerRequest req) {
        if (StringUtils.startsWith(req.getContentType(), "multipart/")) {
            FileItem item = null;
            try {
                item = req.getFileItem("json");
            } catch (ServletException e) {
                // if we bomb out here, so will req.getSubmittedForm()
                return false;
            } catch (IOException e) {
                // if we bomb out here, so will req.getSubmittedForm()
                return false;
            }
            if (item != null) {
                return true;
            }
        } else {
            return req.getParameter("json") != null;
        }
        return false;
    }

    public String getIconFileName() {
        return owner != null && isDeployPossible(owner)
                ? Jenkins.RESOURCE_PATH + "/plugin/cloudbees-deployer-plugin/images/24x24/deploy-now.png"
                : null;
    }

    public String getDisplayName() {
        return Messages.DeployNowRunAction_DisplayName();
    }

    public Set<DeploySourceOrigin> getSources() {
        return Collections.singleton(DeploySourceOrigin.RUN);
    }

    public String getUrlName() {
        return "deploy-now";
    }

    public void onLoad() {

    }

    public void onAttached(Run r) {
        owner = r;
    }

    public void onBuildComplete() {

    }

    public Run getOwner() {
        return owner;
    }

    public boolean isHasOutput() {
        return deployer != null || getLogFile().isFile();
    }

    /**
     * Returns the log file.
     */
    public File getLogFile() {
        return new File(owner.getRootDir(), "cloudbees-deploy-now.log");
    }

    /**
     * Returns true if the log file is still being updated.
     */
    public boolean isLogUpdated() {
        if (deployer != null) {
            return true;
        }
        for (Queue.Item item : Hudson.getInstance().getQueue().getItems()) {
            if (item.task instanceof DeployNowTask && owner.equals(DeployNowTask.class.cast(item.task).getBuild())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the charset in which the log file is written.
     *
     * @return never null.
     */
    public final Charset getCharset() {
        return owner.getCharset();
    }

    /**
     * Sends out the raw console output.
     */
    public void doDeployText(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setContentType("text/plain;charset=UTF-8");
        // Prevent jelly from flushing stream so Content-Length header can be added afterwards
        FlushProofOutputStream out = new FlushProofOutputStream(rsp.getCompressedOutputStream(req));
        try {
            getLogText().writeLogTo(0, out);
        } catch (IOException e) {
            // see comment in writeLogTo() method
            InputStream input = getLogInputStream();
            try {
                IOUtils.copy(input, out);
            } finally {
                IOUtils.closeQuietly(input);
            }
        }
        out.close();
    }

    /**
     * Returns an input stream that reads from the log file.
     * It will use a gzip-compressed log file (log.gz) if that exists.
     *
     * @return an input stream from the log file, or null if none exists
     * @throws IOException
     * @since 1.349
     */
    public InputStream getLogInputStream() throws IOException {
        File logFile = getLogFile();
        if (logFile.exists()) {
            return new FileInputStream(logFile);
        }

        File compressedLogFile = new File(logFile.getParentFile(), logFile.getName() + ".gz");
        if (compressedLogFile.exists()) {
            return new GZIPInputStream(new FileInputStream(compressedLogFile));
        }

        return new NullInputStream(0);
    }

    public Reader getLogReader() throws IOException {
        if (getCharset() == null) {
            return new InputStreamReader(getLogInputStream());
        } else {
            return new InputStreamReader(getLogInputStream(), getCharset());
        }
    }

    /**
     * Used from <tt>console.jelly</tt> to write annotated log to the given output.
     *
     * @since 1.349
     */
    public void writeLogTo(long offset, XMLOutput out) throws IOException {
        try {
            getLogText().writeHtmlTo(offset, out.asWriter());
        } catch (IOException e) {
            // try to fall back to the old getLogInputStream()
            // mainly to support .gz compressed files
            // In this case, console annotation handling will be turned off.
            InputStream input = getLogInputStream();
            try {
                IOUtils.copy(input, out.asWriter());
            } finally {
                IOUtils.closeQuietly(input);
            }
        }
    }

    /**
     * Used to URL-bind {@link hudson.console.AnnotatedLargeText}.
     */
    public AnnotatedLargeText getLogText() {
        return new AnnotatedLargeText(getLogFile(), getCharset(), !isLogUpdated(), this);
    }


    public List<String> getLog(int maxLines) throws IOException {
        int lineCount = 0;
        List<String> logLines = new LinkedList<String>();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(new FileInputStream(getLogFile()), getCharset()));
        try {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                logLines.add(line);
                ++lineCount;
                // If we have too many lines, remove the oldest line.  This way we
                // never have to hold the full contents of a huge log file in memory.
                // Adding to and removing from the ends of a linked list are cheap
                // operations.
                if (lineCount > maxLines) {
                    logLines.remove(0);
                }
            }
        } finally {
            reader.close();
        }

        // If the log has been truncated, include that information.
        // Use set (replaces the first element) rather than add so that
        // the list doesn't grow beyond the specified maximum number of lines.
        if (lineCount > maxLines) {
            logLines.set(0, "[...truncated " + (lineCount - (maxLines - 1)) + " lines...]");
        }

        return ConsoleNote.removeNotes(logLines);

    }

    protected final void run(Deployer deployer) {
        if (this.deployer != null) {
            return;     // already deploying.
        }

        getLogFile().delete();

        StreamBuildListener listener = null;

        this.deployer = deployer;
        Result result = Result.SUCCESS;
        try {
            // to set the state to COMPLETE in the end, even if the thread dies abnormally.
            // otherwise the queue state becomes inconsistent

            long start = System.currentTimeMillis();

            OutputStream logger = null;
            try {
                try {
                    // don't do buffering so that what's written to the listener
                    // gets reflected to the file immediately, which can then be
                    // served to the browser immediately
                    logger = new FileOutputStream(getLogFile());

                    // Global log filters
                    for (ConsoleLogFilter filter : ConsoleLogFilter.all()) {
                        logger = filter.decorateLogger((AbstractBuild) owner, logger);
                    }

                    // Project specific log filters
                    if (owner.getParent() instanceof BuildableItemWithBuildWrappers && owner instanceof
                            AbstractBuild) {
                        BuildableItemWithBuildWrappers biwbw = (BuildableItemWithBuildWrappers) owner.getParent();
                        for (BuildWrapper bw : biwbw.getBuildWrappersList()) {
                            logger = bw.decorateLogger((AbstractBuild) owner, logger);
                        }
                    }

                    listener = new StreamBuildListener(logger, getCharset());

                    listener.started(deployer.getCauses());
                } catch (ThreadDeath t) {
                    throw t;
                } catch (InterruptedException e) {
                    listener.getLogger().println(hudson.model.Messages.Run_BuildAborted());
                }

                Launcher launcher = Jenkins.getInstance().createLauncher(listener);
                if (!deployer.perform((AbstractBuild) owner, launcher, listener)) {
                    result = Result.FAILURE;
                }
                owner.save(); // update any build actions that have been applied
            } catch (ThreadDeath t) {
                result = Result.FAILURE;
                throw t;
            } catch (IOException e) {
                result = Result.FAILURE;
            } catch (InterruptedException e) {
                result = Result.ABORTED;
                if (listener != null) {
                    listener.getLogger().println(hudson.model.Messages.Run_BuildAborted());
                }
            } finally {
                long end = System.currentTimeMillis();
                long duration = Math.max(end - start, 0);

                if (listener != null) {
                    listener.getLogger().println("Duration: " + Util.getTimeSpanString(duration));
                    listener.finished(result);
                    listener.closeQuietly();
                }
                IOUtils.closeQuietly(logger);
            }
        } finally {
            this.deployer = null;
        }
    }


    @Extension
    public static class RunListenerImpl extends RunListener<Run> {
        @Override
        public void onStarted(Run run, TaskListener listener) {
            if (run.getActions(DeployNowRunAction.class).isEmpty()) {
                try {
                    run.addAction(new DeployNowRunAction());
                } catch (Throwable e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Now that we are past 1.458 we can rely on TransientBuildActionFactory.
     */
    @Extension
    public static class TransientBuildActionFactoryImpl extends TransientBuildActionFactory {
        @Override
        public Collection<? extends Action> createFor(Run target) {
            Job job = target.getParent();
            if (job instanceof AbstractProject && CapabilitiesResolver.of((AbstractProject)job).isInstantApplicable()) {
                if (target.getActions(DeployNowRunAction.class).isEmpty()) {
                    return Collections.singleton(new DeployNowRunAction(target));
                }
            }
            return Collections.emptySet();
        }
    }

    public static class Deployer {

        private final List<DeployHost<?, ?>> sets;

        private final List<Cause> causes;

        private final Authentication authentication;

        public Deployer(List<DeployHost<?, ?>> sets, List<Cause> causes, Authentication authentication) {
            this.sets = sets;
            this.causes = causes;
            this.authentication = authentication;
        }

        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            ACL acl = build.getProject().getACL();
            List<Authentication> deployAuthentications = new ArrayList<Authentication>();
            if (acl.hasPermission(authentication, OWN_AUTH)) {
                deployAuthentications.add(authentication);
            }
            if (acl.hasPermission(authentication, JOB_AUTH)) {
                deployAuthentications.add(ACL.SYSTEM);
            }
            try {
                for (DeployHost<? extends DeployHost<?, ?>, ? extends DeployTarget<?>> set : sets) {
                    if (!Engine.create(set)
                            .withCredentials(build.getProject(), ACL.SYSTEM)
                            .from(build, DeploySourceOrigin.RUN)
                            .withLauncher(launcher)
                            .withListener(listener)
                            .build()
                            .perform()) {
                        return false;
                    }
                }
            } catch (Throwable t) {
                // deployment failed - > fail the build
                t.printStackTrace(listener.getLogger());
                return false;
            }
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Deployer deployer = (Deployer) o;

            if (sets != null
                    ? !sets.equals(deployer.sets)
                    : deployer.sets != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return sets != null ? sets.hashCode() : 0;
        }

        @Override
        public String toString() {
            return sets.toString();
        }

        public String getDisplayName() {
            StringBuilder buf = new StringBuilder();
            boolean first = true;
            buf.append("[");
            for (DeployHost<?, ?> set : sets) {
                if (first) {
                    first = false;
                } else {
                    buf.append(", ");
                }
                buf.append(set.getDisplayName());
            }
            buf.append("]");
            return buf.toString();
        }

        public List<Cause> getCauses() {
            return causes;
        }
    }

}
