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
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import org.testng.annotations.AfterMethod;
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

import static java.lang.String.format;
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
    private AsyncHttpClient client;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        resource = new TestResource();
        server = createServer(resource);

        client = new AsyncHttpClient();

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
        catch (Throwable e) {
            // ignore
        }

        try {
            if (client != null) {
                client.close();
            }
        }
        catch (Throwable e) {
            // ignore
        }
    }

    @Test
    public void testDeleteViaQueryParam()
            throws Exception
    {
        client.prepareRequest(buildRequestWithQueryParam(POST, DELETE))
                .execute()
                .get();

        assertFalse(resource.postCalled(), "POST");
        assertTrue(resource.deleteCalled(), "DELETE");
        assertFalse(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }

    @Test
    public void testPutViaQueryParam()
            throws Exception
    {
        client.prepareRequest(buildRequestWithQueryParam(POST, PUT))
                .execute()
                .get();

        assertFalse(resource.postCalled(), "POST");
        assertFalse(resource.deleteCalled(), "DELETE");
        assertTrue(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }


    @Test
    public void testPostViaQueryParam()
            throws Exception
    {
        client.prepareRequest(buildRequestWithQueryParam(POST, POST))
                .execute()
                .get();

        assertTrue(resource.postCalled(), "POST");
        assertFalse(resource.deleteCalled(), "DELETE");
        assertFalse(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }

    @Test
    public void testDeleteViaHeader()
            throws Exception
    {
        client.prepareRequest(buildRequestWithHeader(POST, DELETE))
                .execute()
                .get();

        assertFalse(resource.postCalled(), "POST");
        assertTrue(resource.deleteCalled(), "DELETE");
        assertFalse(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }

    @Test
    public void testPutViaHeader()
            throws Exception
    {
        client.prepareRequest(buildRequestWithHeader(POST, PUT))
                .execute()
                .get();

        assertFalse(resource.postCalled(), "POST");
        assertFalse(resource.deleteCalled(), "DELETE");
        assertTrue(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }


    @Test
    public void testPostViaHeader()
            throws Exception
    {
        client.prepareRequest(buildRequestWithHeader(POST, POST))
                .execute()
                .get();

        assertTrue(resource.postCalled(), "POST");
        assertFalse(resource.deleteCalled(), "DELETE");
        assertFalse(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }


    private void assertNonOverridableMethod(Request request)
            throws IOException, ExecutionException, InterruptedException
    {
        Response response = client.prepareRequest(request)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode());
        assertFalse(resource.postCalled(), "POST");
        assertFalse(resource.deleteCalled(), "DELETE");
        assertFalse(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }

    private Request buildRequestWithHeader(String type, String override)
    {
        return new RequestBuilder(type)
                .setUrl(server.getBaseUrl().toString())
                .addHeader("X-HTTP-Method-Override", override)
                .build();
    }

    private Request buildRequestWithQueryParam(String type, String override)
    {
        String url = server.getBaseUrl().resolve(format("/?_method=%s", override)).toString();

        return new RequestBuilder(type)
                .setUrl(url)
                .build();
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

    private TestingHttpServer createServer(final TestResource resource)
    {
        return Guice.createInjector(new JaxrsModule(),
                                    new TestingHttpServerModule(),
                                    new Module()
                                    {
                                        @Override
                                        public void configure(Binder binder)
                                        {
                                            binder.bind(TestResource.class).toInstance(resource);
                                        }
                                    }).getInstance(TestingHttpServer.class);
    }
}
