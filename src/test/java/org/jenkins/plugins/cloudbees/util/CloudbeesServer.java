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

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * @author Olivier Lamy
 */
public class CloudbeesServer {

    Server server = null;

    int port;

    public CloudbessServlet cloudbessServlet = new CloudbessServlet();


    public void startServer()
            throws Exception {
        System.out.println("start cloudbees server");
        this.server = new Server(0);
        Context context = new Context(this.server, "/", 0);
        context.addServlet(new ServletHolder(cloudbessServlet), "/*");
        this.server.start();
        Connector connector = this.server.getConnectors()[0];
        this.port = connector.getLocalPort();
    }

    public void stopServer()
            throws Exception {
        if (this.server != null && this.server.isRunning()) {
            this.server.stop();
        }
    }

    public int getPort() {
        return port;
    }

    public static class CloudbessServlet extends HttpServlet {

        public List<FileItem> items;

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            //TODO take care of &format=xml/json
            if (req.getMethod().equalsIgnoreCase("get")) {
                String action = req.getParameter("action");
                if (action.equals("application.list")) {
                    String response = XmlResponseGenerator.applicationListResponse();
                    resp.getWriter().print(response);
                    return;
                }
                if (action.equals("application.checkSums")) {
                    String response = XmlResponseGenerator.applicationCheckSumsResponse();
                    resp.getWriter().print(response);
                    return;
                }
                if (action.equals("say.hello")) {
                    String response = XmlResponseGenerator.sayHelloResponse();
                    resp.getWriter().print(response);
                    return;
                }
            } else if (req.getMethod().equalsIgnoreCase("post")) {
                boolean isMultipart = ServletFileUpload.isMultipartContent(req);
                if (isMultipart) {
                    FileItemFactory factory = new DiskFileItemFactory();
                    ServletFileUpload upload = new ServletFileUpload(factory);
                    try {
                        items = upload.parseRequest(req);
                    } catch (FileUploadException e) {
                        throw new ServletException(e.getMessage(), e);
                    }
                    String response = XmlResponseGenerator.applicationDeployArchiveResponse();
                    resp.getWriter().print(response);
                }
                return;
            }
        }
    }

}
