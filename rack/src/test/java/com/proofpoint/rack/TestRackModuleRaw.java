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
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.node.testing.TestingNodeModule;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestRackModuleRaw
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
                new TestingDiscoveryModule(),
                new ConfigurationModule(new ConfigurationFactory(
                        ImmutableMap.<String, String>builder()
                                .put("rackserver.rack-config-path", Resources.getResource("test/raw/config.ru").getFile())
                                .build()
                )));

        server = injector.getInstance(TestingHttpServer.class);
        server.start();
        client = new AsyncHttpClient();
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testBasicGet()
            throws Throwable
    {
        Response response = client.prepareGet(server.getBaseUrl().resolve("/foo").toString())
                .execute()
                .get();

        assertEquals(response.getStatusCode(), 200);
        JsonNode tree = MAPPER.readTree(response.getResponseBody());

        assertEquals(tree.get("REQUEST_METHOD").getTextValue(), "GET");
        assertEquals(tree.get("SCRIPT_NAME").getTextValue(), "");
        assertEquals(tree.get("PATH_INFO").getTextValue(), "/foo");
        assertEquals(tree.get("QUERY_STRING").getTextValue(), "");
    }

    @Test
    public void testGetWithEscaping()
            throws Throwable
    {
        String path = "/hello%2Fworld+bye";

        Response response = client.prepareGet(server.getBaseUrl().resolve(path).toString())
                .addQueryParameter("foo/bar", "123 999")
                .execute()
                .get();

        assertEquals(response.getStatusCode(), 200);
        JsonNode tree = MAPPER.readTree(response.getResponseBody());

        assertEquals(tree.get("REQUEST_METHOD").getTextValue(), "GET");
        assertEquals(tree.get("SCRIPT_NAME").getTextValue(), "");
        assertEquals(tree.get("PATH_INFO").getTextValue(), path);
        assertEquals(tree.get("QUERY_STRING").getTextValue(), "foo%2Fbar=123%20999");
    }
}
