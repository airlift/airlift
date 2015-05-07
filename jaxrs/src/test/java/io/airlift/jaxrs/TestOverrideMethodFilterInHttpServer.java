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
package io.airlift.jaxrs;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Module;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.json.JsonModule;
import io.airlift.node.testing.TestingNodeModule;
import io.airlift.testing.Closeables;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response.Status;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test(singleThreaded = true)
public class TestOverrideMethodFilterInHttpServer
{
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String PUT = "PUT";
    private static final String DELETE = "DELETE";

    private TestingHttpServer server;
    private TestResource resource;
    private HttpClient client;

    @BeforeClass
    public void setup()
            throws Exception
    {
        resource = new TestResource();
        server = createServer(resource);

        client = new JettyHttpClient();

        server.start();
    }

    @AfterClass(alwaysRun = true)
    public void teardown()
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

    @BeforeMethod
    public void resetResource()
    {
        resource.reset();
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

    @Path("/")
    public static class TestResource
    {
        private volatile boolean post;
        private volatile boolean put;
        private volatile boolean get;
        private volatile boolean delete;

        public void reset()
        {
            post = false;
            put = false;
            get = false;
            delete = false;
        }

        @POST
        public void post()
        {
            post = true;
        }

        @GET
        public boolean get()
        {
            get = true;
            return true;
        }

        @DELETE
        public void delete()
        {
            delete = true;
        }

        @PUT
        public void put()
        {
            put = true;
        }

        public boolean postCalled()
        {
            return post;
        }

        public boolean putCalled()
        {
            return put;
        }

        public boolean getCalled()
        {
            return get;
        }

        public boolean deleteCalled()
        {
            return delete;
        }
    }

    private static TestingHttpServer createServer(final TestResource resource)
    {
        return Guice.createInjector(
                new TestingNodeModule(),
                new JaxrsModule(true),
                new JsonModule(),
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
