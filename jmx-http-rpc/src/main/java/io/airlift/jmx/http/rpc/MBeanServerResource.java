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

import com.google.inject.Inject;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.remote.JMXServerErrorException;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import java.io.InputStream;
import java.io.ObjectInputStream;

import static io.airlift.jmx.http.rpc.HttpMBeanServerRpc.createExceptionResponse;
import static io.airlift.jmx.http.rpc.HttpMBeanServerRpc.createSuccessResponse;

@Path("/v1/jmx/mbeanServer")
public class MBeanServerResource
{
    private final MBeanServer mbeanServer;
    private final HttpMBeanServerCredentials credentials;

    @Inject
    public MBeanServerResource(MBeanServer mbeanServer, HttpMBeanServerCredentials credentials)
    {
        this.mbeanServer = mbeanServer;
        this.credentials = credentials;
    }

    @POST
    @Path("{method}")
    public Response invoke(@PathParam("method") String method, InputStream in, @HeaderParam("Authorization") String authHeader)
            throws Exception
    {
        if (credentials != null) {
            if (!credentials.authenticate(HttpMBeanServerCredentials.fromBasicAuthHeader(authHeader))) {
                return Response.status(Status.UNAUTHORIZED).entity(createExceptionResponse(new SecurityException("Invalid credentials"))).build();
            }
        }

        if (method == null) {
            return Response.status(Status.BAD_REQUEST).entity(createExceptionResponse(new NullPointerException("method is null"))).build();
        }

        Object[] args;
        try {
            args = (Object[]) new ObjectInputStream(in).readObject();
        }
        catch (Exception e) {
            return Response.status(Status.BAD_REQUEST).entity(createExceptionResponse(new IllegalArgumentException("Request does not contain a serialized Object[]"))).build();
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
                return Response.status(Status.BAD_REQUEST).entity(createExceptionResponse(new IllegalArgumentException("Unknown method " + method))).build();
            }

            return Response.ok(createSuccessResponse(result)).build();
        }
        catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(createExceptionResponse(e)).build();
        }
        catch (Error e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(createExceptionResponse(new JMXServerErrorException("Internal error", e))).build();
        }
    }
}
