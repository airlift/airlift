/*
 * Copyright 2010 Proofpoint, Inc.
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
package io.airlift.jmx.http.rpc;

import javax.inject.Inject;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.remote.JMXServerErrorException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.ObjectInputStream;

import static io.airlift.jmx.http.rpc.HttpMBeanServerRpc.createExceptionResponse;
import static io.airlift.jmx.http.rpc.HttpMBeanServerRpc.createSuccessResponse;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

public class MBeanServerServlet extends HttpServlet
{
    private static final String BASE_PATH = "/v1/jmx/mbeanServer/";
    private final MBeanServer mbeanServer;
    private final HttpMBeanServerCredentials credentials;

    @Inject
    public MBeanServerServlet(MBeanServer mbeanServer, HttpMBeanServerCredentials credentials)
    {
        this.mbeanServer = mbeanServer;
        this.credentials = credentials;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        if (credentials != null) {
            if (!credentials.authenticate(HttpMBeanServerCredentials.fromBasicAuthHeader(request.getHeader("Authorization")))) {
                sendErrorResponse(response, SC_UNAUTHORIZED, new SecurityException("Invalid credentials"));
                return;
            }
        }

        String method = extractJmxMethodFromPath(request);
        if (method == null) {
            sendErrorResponse(response, SC_BAD_REQUEST, new NullPointerException("method is null"));
            return;
        }

        Object[] args;
        try {
            args = (Object[]) new ObjectInputStream(request.getInputStream()).readObject();
        }
        catch (Exception e) {
            sendErrorResponse(response, SC_BAD_REQUEST, new IllegalArgumentException("Request does not contain a serialized Object[]"));
            return;
        }

        try {
            Object result = null;
            if ("getMBeanInfo".equals(method)) {
                result = mbeanServer.getMBeanInfo((ObjectName) args[0]);
            }
            else if ("queryMBeans".equals(method)) {
                result = mbeanServer.queryMBeans((ObjectName) args[0], (QueryExp) args[1]);
            }
            else if ("queryNames".equals(method)) {
                result = mbeanServer.queryNames((ObjectName) args[0], (QueryExp) args[1]);
            }
            else if ("getAttribute".equals(method)) {
                result = mbeanServer.getAttribute((ObjectName) args[0], (String) args[1]);
            }
            else if ("getAttributes".equals(method)) {
                result = mbeanServer.getAttributes((ObjectName) args[0], (String[]) args[1]);
            }
            else if ("setAttribute".equals(method)) {
                mbeanServer.setAttribute((ObjectName) args[0], (Attribute) args[1]);
            }
            else if ("setAttributes".equals(method)) {
                result = mbeanServer.setAttributes((ObjectName) args[0], (AttributeList) args[1]);
            }
            else if ("invoke".equals(method)) {
                result = mbeanServer.invoke((ObjectName) args[0], (String) args[1], (Object[]) args[2], (String[]) args[3]);
            }
            else if ("getMBeanCount".equals(method)) {
                result = mbeanServer.getMBeanCount();
            }
            else if ("isRegistered".equals(method)) {
                result = mbeanServer.isRegistered((ObjectName) args[0]);
            }
            else if ("getObjectInstance".equals(method)) {
                result = mbeanServer.getObjectInstance((ObjectName) args[0]);
            }
            else if ("getDefaultDomain".equals(method)) {
                result = mbeanServer.getDefaultDomain();
            }
            else if ("getDomains".equals(method)) {
                result = mbeanServer.getDomains();
            }
            else if ("isInstanceOf".equals(method)) {
                result = mbeanServer.isInstanceOf((ObjectName) args[0], (String) args[1]);
            }
            else {
                sendErrorResponse(response, SC_BAD_REQUEST, new IllegalArgumentException("Unknown method " + method));
                return;
            }

            response.getOutputStream().write(createSuccessResponse(result));
        }
        catch (Exception e) {
            sendErrorResponse(response, SC_INTERNAL_SERVER_ERROR, e);
        }
        catch (Error e) {
            sendErrorResponse(response, SC_INTERNAL_SERVER_ERROR, new JMXServerErrorException("Internal error", e));
        }
    }

    private String extractJmxMethodFromPath(HttpServletRequest request)
    {
        String path = request.getRequestURI();
        if (!path.startsWith(BASE_PATH)) {
            return null;
        }
        path = path.substring("/v1/jmx/mbeanServer/".length());
        if (path.contains("/")) {
            return null;
        }
        return path;
    }

    private void sendErrorResponse(HttpServletResponse response, int status, Exception exception)
            throws IOException
    {
        response.setStatus(status);
        response.getOutputStream().write(createExceptionResponse(exception));
    }
}
