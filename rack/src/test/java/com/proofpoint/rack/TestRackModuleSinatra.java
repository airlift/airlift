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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.inject.Injector;
import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.http.client.ApacheHttpClient;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.jmx.testing.TestingJmxModule;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.guice.MBeanModule;

import java.util.Map;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapApplication;
import static com.proofpoint.http.client.JsonResponseHandler.createJsonResponseHandler;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.Request.Builder.preparePost;
import static com.proofpoint.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;

public class TestRackModuleSinatra
{
    private HttpClient client;
    private TestingHttpServer server;
    private LifeCycleManager lifeCycleManager;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        Bootstrap app = bootstrapApplication("test-application")
                .withModules(
                        new TestingHttpServerModule(),
                        new RackModule(),
                        new TestingNodeModule(),
                        new TestingDiscoveryModule(),
                        new ReportingModule(),
                        new MBeanModule(),
                        new TestingJmxModule()
                );

        Injector injector = app
                .doNotInitializeLogging()
                .setRequiredConfigurationProperty("rackserver.rack-config-path", Resources.getResource("test/sinatra/config.ru").getFile())
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingHttpServer.class);
        client = new ApacheHttpClient();
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        if (lifeCycleManager != null) {
            lifeCycleManager.stop();
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
