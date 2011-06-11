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
package com.proofpoint.rack;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.node.testing.TestingNodeModule;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestRackModule
{
    private AsyncHttpClient client;
    private TestingHttpServer server;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new TestingHttpServerModule(),
                new RackModule(),
                new TestingNodeModule(),
                new ConfigurationModule(new ConfigurationFactory(
                        ImmutableMap.<String, String>builder()
                                .put("rackserver.rack_config_path", "config.ru")
                                .build()
                )));

        server = injector.getInstance(TestingHttpServer.class);
        server.start();
        client = new AsyncHttpClient();
    }

    @Test
    public void testGet()
            throws Throwable
    {
        String expected = "FooBarBaz";

        Response response = client.prepareGet(server.getBaseUrl().resolve("/name-echo").toString())
                .addQueryParameter("name", expected)
                .execute()
                .get();

        assertEquals(response.getResponseBody(), expected);
        assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void testGet404()
            throws Throwable
    {
        Response response = client.prepareGet(server.getBaseUrl().resolve("/nothing-here").toString())
                .execute()
                .get();

        assertEquals(response.getStatusCode(), 404);
    }

    @Test
    public void testSettingCookiesResultsInACookieHashInRuby()
            throws Throwable
    {
        Response response = client.prepareGet(server.getBaseUrl().resolve("/header-list-test").toString())
                .addHeader("COOKIE", "Cookie1=Value1")
                .addHeader("COOKIE", "Cookie2=Value2")
                .execute()
                .get();

        assertEquals(response.getResponseBody(), "{\"Cookie1\"=>\"Value1\", \"Cookie2\"=>\"Value2\"}");
        assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void testPostAndGet()
            throws Throwable
    {
        String expected = "FooBarBaz";

        Response responsePost = client.preparePost(server.getBaseUrl().resolve("/temp-store").toString())
                .setBody(expected)
                .execute()
                .get();

        assertEquals(responsePost.getResponseBody(), "");
        assertEquals(responsePost.getStatusCode(), 201);

        Response responseGet = client.prepareGet(server.getBaseUrl().resolve("/temp-store").toString())
                .execute()
                .get();

        assertEquals(responseGet.getResponseBody(), expected);
        assertEquals(responseGet.getStatusCode(), 200);
    }
}
