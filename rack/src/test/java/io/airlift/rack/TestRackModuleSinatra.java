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
package io.airlift.rack;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationModule;
import io.airlift.discovery.client.testing.TestingDiscoveryModule;
import io.airlift.http.client.ApacheHttpClient;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.json.JsonCodec;
import io.airlift.node.testing.TestingNodeModule;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;

public class TestRackModuleSinatra
{
    private HttpClient client;
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
                                .put("rackserver.rack-config-path", Resources.getResource("test/sinatra/config.ru").getFile())
                                .build()
                )));

        server = injector.getInstance(TestingHttpServer.class);
        server.start();
        client = new ApacheHttpClient();
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
    public void testGet()
            throws Throwable
    {
        String expected = "FooBarBaz";

        StringResponse response = client.execute(
                prepareGet().setUri(server.getBaseUrl().resolve(format("/name-echo?name=%s", expected))).build(),
                createStringResponseHandler());

        assertEquals(response.getBody(), expected);
        assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void testGet404()
            throws Throwable
    {
        StatusResponse response = client.execute(
                prepareGet().setUri(server.getBaseUrl().resolve("/nothing-here")).build(),
                createStatusResponseHandler());

        assertEquals(response.getStatusCode(), 404);
    }

    @Test
    public void testSettingCookiesResultsInACookieHashInRuby()
            throws Throwable
    {
        Map<String, String> response = client.execute(
                prepareGet()
                        .setUri(server.getBaseUrl().resolve("/header-cookies-json"))
                        .addHeader("COOKIE", "Cookie1=Value1")
                        .addHeader("COOKIE", "Cookie2=Value2")
                        .build(),
                createJsonResponseHandler(JsonCodec.mapJsonCodec(String.class, String.class)));

        assertEquals(response, ImmutableMap.of("Cookie1","Value1","Cookie2","Value2"));
    }

    @Test
    public void testPostAndGet()
            throws Throwable
    {
        String expected = "FooBarBaz";

        StringResponse responsePost = client.execute(
                preparePost()
                        .setUri(server.getBaseUrl().resolve("/temp-store"))
                        .setBodyGenerator(createStaticBodyGenerator(expected.getBytes(Charsets.UTF_8)))
                        .build(),
                createStringResponseHandler());

        assertEquals(responsePost.getBody(), "");
        assertEquals(responsePost.getStatusCode(), 201);

        StringResponse responseGet = client.execute(
                prepareGet().setUri(server.getBaseUrl().resolve("/temp-store")).build(),
                createStringResponseHandler());

        assertEquals(responseGet.getBody(), expected);
        assertEquals(responseGet.getStatusCode(), 200);
    }

    @Test
    public void testResponseIsClosed()
            throws Throwable
    {
        StringResponse response = client.execute(
                prepareGet().setUri(server.getBaseUrl().resolve("/closable-response")).build(),
                createStringResponseHandler());

        assertEquals(response.getBody(), "hello");
        assertEquals(response.getStatusCode(), 200);

        response = client.execute(
                prepareGet().setUri(server.getBaseUrl().resolve("/close-called")).build(),
                createStringResponseHandler());

        assertEquals(response.getBody(), "true");
        assertEquals(response.getStatusCode(), 200);
    }
}
