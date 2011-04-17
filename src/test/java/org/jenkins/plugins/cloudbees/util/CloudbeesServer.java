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
        throws Exception
    {
        System.out.println("start cloudbees server");
        this.server = new Server( 0 );
        Context context = new Context( this.server, "/", 0 );
        context.addServlet( new ServletHolder(cloudbessServlet), "/*" );
        this.server.start();
        Connector connector = this.server.getConnectors()[0];
        this.port = connector.getLocalPort();
    }

    public void stopServer()
        throws Exception
    {
        if ( this.server != null && this.server.isRunning() )
        {
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
            if (req.getMethod().equalsIgnoreCase("get") ) {
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
                if (action.equals( "say.hello" )) {
                    String response = XmlResponseGenerator.sayHelloResponse();
                    resp.getWriter().print(response);
                    return;
                }
            } else if (req.getMethod().equalsIgnoreCase("post") ) {
                boolean isMultipart = ServletFileUpload.isMultipartContent(req);
                if (isMultipart) {
                    FileItemFactory factory = new DiskFileItemFactory();
                    ServletFileUpload upload = new ServletFileUpload(factory);
                    try {
                        items = upload.parseRequest(req);
                    } catch (FileUploadException e) {
                        throw  new ServletException(e.getMessage(),e);
                    }
                String response = XmlResponseGenerator.applicationDeployArchiveResponse();
                resp.getWriter().print(response);
                }
                return;
            }
        }
    }

}
