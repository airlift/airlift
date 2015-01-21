/*
* Copyright 2015 Proofpoint, Inc.
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
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static java.lang.String.format;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;

@Test(singleThreaded = true)
public class TestQueryParamExceptionMapper
{

    private static final String GET = "GET";

    private TestingHttpServer server;
    private TestQueryParamResource resource;
    private HttpClient client;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        resource = new TestQueryParamResource();
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
    public void testGetWithValidQueryParamSucceeds()
            throws Exception
    {
        StatusResponse response = client.execute(buildRequestWithQueryParam(GET, "123"), createStatusResponseHandler());
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
    }

    @Test
    public void testGetWithInvalidQueryParamReturnsBadRequest()
            throws Exception
    {
        StatusResponse response = client.execute(buildRequestWithQueryParam(GET, "string"), createStatusResponseHandler());
        assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode());
    }

    private Request buildRequestWithQueryParam(String type, String override)
    {
        return Request.builder().setUri(server.getBaseUrl().resolve(format("/?count=%s", override))).setMethod(type).build();
    }

    private static TestingHttpServer createServer(final TestQueryParamResource resource)
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

    @Path("/")
    public class TestQueryParamResource
    {
        @GET
        public Response get(@QueryParam("count") Integer count)
        {
            return Response.ok().build();
        }
    }

}
