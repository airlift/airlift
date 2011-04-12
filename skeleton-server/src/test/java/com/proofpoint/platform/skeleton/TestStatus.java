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
package com.proofpoint.platform.skeleton;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.jaxrs.JaxrsModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import static org.testng.Assert.assertEquals;

public class TestStatus
{
    private static final int NOT_ALLOWED = 405;

    private AsyncHttpClient client;
    private TestingHttpServer server;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        // TODO: wrap all this stuff in a TestBootstrap class
        Injector injector = Guice.createInjector(new TestingHttpServerModule(),
                new TestingNodeModule(),
                new JsonModule(),
                new JaxrsModule(),
                new MainModule(),
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())));

        server = injector.getInstance(TestingHttpServer.class);

        server.start();
        client = new AsyncHttpClient();
    }

    @AfterMethod
    public void teardown()
            throws Exception
    {
        if (server != null) {
            server.stop();
        }

        if (client != null) {
            client.close();
        }
    }

    @Test
    public void testGetStatus()
            throws IOException, ExecutionException, InterruptedException
    {
        Response response = client.prepareGet(urlFor("/v1/status"))
                .execute()
                .get();

        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
    }

    @Test
    public void testPostNotAllowed()
            throws IOException, ExecutionException, InterruptedException
    {
        Response response = client.preparePost(urlFor("/v1/status"))
                .execute()
                .get();

        assertEquals(response.getStatusCode(), NOT_ALLOWED);
    }

    @Test
    public void testPutNotAllowed()
            throws IOException, ExecutionException, InterruptedException
    {
        Response response = client.preparePut(urlFor("/v1/status"))
                .execute()
                .get();

        assertEquals(response.getStatusCode(), NOT_ALLOWED);
    }

    @Test
    public void testDeleteNotAllowed()
            throws IOException, ExecutionException, InterruptedException
    {
        Response response = client.prepareDelete(urlFor("/v1/status"))
                .execute()
                .get();

        assertEquals(response.getStatusCode(), NOT_ALLOWED);
    }

    private String urlFor(String path)
    {
        return server.getBaseUrl().resolve(path).toString();
    }
}
