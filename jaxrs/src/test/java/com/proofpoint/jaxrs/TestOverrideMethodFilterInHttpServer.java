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
package com.proofpoint.jaxrs;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.ApplicationNameModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.testing.Closeables;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.management.MBeanServer;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static java.lang.String.format;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestOverrideMethodFilterInHttpServer
{
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String PUT = "PUT";
    private static final String DELETE = "DELETE";

    private TestingHttpServer server;
    private TestResource resource;
    private HttpClient client;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        resource = new TestResource();
        server = createServer(resource);

        client = new JettyHttpClient();

        server.start();
    }

    @AfterMethod
    public void teardown()
            throws Exception
    {
        try {
            if (server != null) {
                server.stop();
            }
        }
        catch (Throwable ignored) {
        }
        Closeables.closeQuietly(client);
    }

    @Test
    public void testDeleteViaQueryParam()
            throws Exception
    {
        client.execute(buildRequestWithQueryParam(POST, DELETE), createStatusResponseHandler());

        assertFalse(resource.postCalled(), "POST");
        assertTrue(resource.deleteCalled(), "DELETE");
        assertFalse(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }

    @Test
    public void testPutViaQueryParam()
            throws Exception
    {
        client.execute(buildRequestWithQueryParam(POST, PUT), createStatusResponseHandler());

        assertFalse(resource.postCalled(), "POST");
        assertFalse(resource.deleteCalled(), "DELETE");
        assertTrue(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }


    @Test
    public void testPostViaQueryParam()
            throws Exception
    {
        client.execute(buildRequestWithQueryParam(POST, POST), createStatusResponseHandler());

        assertTrue(resource.postCalled(), "POST");
        assertFalse(resource.deleteCalled(), "DELETE");
        assertFalse(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }

    @Test
    public void testDeleteViaHeader()
            throws Exception
    {
        client.execute(buildRequestWithHeader(POST, DELETE), createStatusResponseHandler());

        assertFalse(resource.postCalled(), "POST");
        assertTrue(resource.deleteCalled(), "DELETE");
        assertFalse(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }

    @Test
    public void testPutViaHeader()
            throws Exception
    {
        client.execute(buildRequestWithHeader(POST, PUT), createStatusResponseHandler());

        assertFalse(resource.postCalled(), "POST");
        assertFalse(resource.deleteCalled(), "DELETE");
        assertTrue(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }


    @Test
    public void testPostViaHeader()
            throws Exception
    {
        client.execute(buildRequestWithHeader(POST, POST), createStatusResponseHandler());

        assertTrue(resource.postCalled(), "POST");
        assertFalse(resource.deleteCalled(), "DELETE");
        assertFalse(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }


    private void assertNonOverridableMethod(Request request)
            throws IOException, ExecutionException, InterruptedException
    {
        StatusResponse response = client.execute(request, createStatusResponseHandler());

        assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode());
        assertFalse(resource.postCalled(), "POST");
        assertFalse(resource.deleteCalled(), "DELETE");
        assertFalse(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }

    private Request buildRequestWithHeader(String type, String override)
    {
        return Request.builder().setUri(server.getBaseUrl()).setMethod(type).addHeader("X-HTTP-Method-Override", override).build();
    }

    private Request buildRequestWithQueryParam(String type, String override)
    {
        return Request.builder().setUri(server.getBaseUrl().resolve(format("/?_method=%s", override))).setMethod(type).build();
    }

    @Test
    public void testNonOverridableMethodsWithHeader()
            throws IOException, ExecutionException, InterruptedException
    {
        assertNonOverridableMethod(buildRequestWithHeader(GET, POST));
        assertNonOverridableMethod(buildRequestWithHeader(GET, DELETE));
        assertNonOverridableMethod(buildRequestWithHeader(GET, PUT));

        assertNonOverridableMethod(buildRequestWithHeader(DELETE, POST));
        assertNonOverridableMethod(buildRequestWithHeader(DELETE, GET));
        assertNonOverridableMethod(buildRequestWithHeader(DELETE, PUT));

        assertNonOverridableMethod(buildRequestWithHeader(PUT, POST));
        assertNonOverridableMethod(buildRequestWithHeader(PUT, DELETE));
        assertNonOverridableMethod(buildRequestWithHeader(PUT, GET));
    }

    @Test
    public void testNonOverridableMethodsWithQueryParam()
            throws IOException, ExecutionException, InterruptedException
    {
        assertNonOverridableMethod(buildRequestWithQueryParam(GET, POST));
        assertNonOverridableMethod(buildRequestWithQueryParam(GET, DELETE));
        assertNonOverridableMethod(buildRequestWithQueryParam(GET, PUT));

        assertNonOverridableMethod(buildRequestWithQueryParam(DELETE, POST));
        assertNonOverridableMethod(buildRequestWithQueryParam(DELETE, GET));
        assertNonOverridableMethod(buildRequestWithQueryParam(DELETE, PUT));

        assertNonOverridableMethod(buildRequestWithQueryParam(PUT, POST));
        assertNonOverridableMethod(buildRequestWithQueryParam(PUT, DELETE));
        assertNonOverridableMethod(buildRequestWithQueryParam(PUT, GET));
    }

    private static TestingHttpServer createServer(final TestResource resource)
    {
        return Guice.createInjector(
                new ApplicationNameModule("test-application"),
                new TestingNodeModule(),
                explicitJaxrsModule(),
                new JsonModule(),
                new ReportingModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(MBeanServer.class).toInstance(mock(MBeanServer.class));
                    }
                },
                new TestingHttpServerModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        jaxrsBinder(binder).bindInstance(resource);
                    }
                }).getInstance(TestingHttpServer.class);
    }
}
