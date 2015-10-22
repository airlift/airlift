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
package com.proofpoint.platform.sample;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.inject.Injector;
import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.event.client.EventClient;
import com.proofpoint.event.client.InMemoryEventClient;
import com.proofpoint.event.client.InMemoryEventModule;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.testing.Closeables;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapApplication;
import static com.proofpoint.http.client.JsonResponseHandler.createJsonResponseHandler;
import static com.proofpoint.http.client.Request.Builder.prepareDelete;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.Request.Builder.preparePost;
import static com.proofpoint.http.client.Request.Builder.preparePut;
import static com.proofpoint.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static com.proofpoint.json.JsonCodec.mapJsonCodec;
import static com.proofpoint.platform.sample.Person.createPerson;
import static com.proofpoint.platform.sample.PersonEvent.personAdded;
import static com.proofpoint.platform.sample.PersonEvent.personRemoved;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestServer
{
    private static final int NOT_ALLOWED = 405;

    private final JsonCodec<Map<String, Object>> mapCodec = mapJsonCodec(String.class, Object.class);
    private final HttpClient client = new JettyHttpClient();

    private LifeCycleManager lifeCycleManager;
    private TestingHttpServer server;
    private PersonStore store;
    private InMemoryEventClient eventClient;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        Bootstrap app = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new TestingNodeModule(),
                        new InMemoryEventModule(),
                        new TestingHttpServerModule(),
                        new JsonModule(),
                        explicitJaxrsModule(),
                        new ReportingModule(),
                        new TestingMBeanModule(),
                        new MainModule()
                )
                .quiet();

        Injector injector = app
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingHttpServer.class);
        store = injector.getInstance(PersonStore.class);
        eventClient = (InMemoryEventClient) injector.getInstance(EventClient.class);
    }

    @AfterMethod
    public void teardown()
            throws Exception
    {
        if (lifeCycleManager != null) {
            lifeCycleManager.stop();
        }
    }

    @AfterClass(alwaysRun = true)
    public void teardownClass()
    {
        Closeables.closeQuietly(client);
    }

    @Test
    public void testEmpty()
            throws Exception
    {
        Map<String, Object> response = client.execute(
                prepareGet().setUri(uriFor("/v1/person")).build(),
                createJsonResponseHandler(mapCodec));

        assertEquals(response, ImmutableMap.of());
    }

    @Test
    public void testGetAll()
            throws IOException, ExecutionException, InterruptedException
    {
        store.put("bar", createPerson("bar@example.com", "Mr Bar"));
        store.put("foo", createPerson("foo@example.com", "Mr Foo"));

        Object expected = ImmutableMap.of(
                "foo", ImmutableMap.of("name", "Mr Foo", "email", "foo@example.com"),
                "bar", ImmutableMap.of("name", "Mr Bar", "email", "bar@example.com")
        );

        Object actual = client.execute(
                prepareGet().setUri(uriFor("/v1/person")).build(),
                createJsonResponseHandler(mapCodec));
        assertEquals(actual, expected);
    }

    @Test
    public void testGetSingle()
            throws IOException, ExecutionException, InterruptedException
    {
        store.put("foo", createPerson("foo@example.com", "Mr Foo"));

        URI requestUri = uriFor("/v1/person/foo");

        Map<String, String> expected = ImmutableMap.of("name", "Mr Foo", "email", "foo@example.com");

        Map<String, Object> actual = client.execute(
                prepareGet().setUri(requestUri).build(),
                createJsonResponseHandler(mapCodec));

        assertEquals(actual, expected);
    }

    @Test
    public void testPut()
            throws IOException, ExecutionException, InterruptedException
    {
        String json = Resources.toString(Resources.getResource("single.json"), Charsets.UTF_8);

        StatusResponse response = client.execute(
                preparePut()
                        .setUri(uriFor("/v1/person/foo"))
                        .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .setBodySource(createStaticBodyGenerator(json, Charsets.UTF_8))
                        .build(),
                createStatusResponseHandler());

        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.CREATED.getStatusCode());

        assertEquals(store.get("foo"), createPerson("foo@example.com", "Mr Foo"));

        assertEquals(eventClient.getEvents(), ImmutableList.of(
                personAdded("foo", createPerson("foo@example.com", "Mr Foo"))
        ));
    }

    @Test
    public void testDelete()
            throws IOException, ExecutionException, InterruptedException
    {
        store.put("foo", createPerson("foo@example.com", "Mr Foo"));

        StatusResponse response = client.execute(
                prepareDelete()
                        .setUri(uriFor("/v1/person/foo"))
                        .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .build(),
                createStatusResponseHandler());

        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.NO_CONTENT.getStatusCode());

        assertNull(store.get("foo"));

        assertEquals(eventClient.getEvents(), ImmutableList.of(
                personAdded("foo", createPerson("foo@example.com", "Mr Foo")),
                personRemoved("foo", createPerson("foo@example.com", "Mr Foo"))
        ));
    }

    @Test
    public void testDeleteMissing()
            throws IOException, ExecutionException, InterruptedException
    {
        StatusResponse response = client.execute(
                prepareDelete()
                        .setUri(uriFor("/v1/person/foo"))
                        .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .build(),
                createStatusResponseHandler());

        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testPostNotAllowed()
            throws IOException, ExecutionException, InterruptedException
    {
        String json = Resources.toString(Resources.getResource("single.json"), Charsets.UTF_8);

        StatusResponse response = client.execute(
                preparePost()
                        .setUri(uriFor("/v1/person/foo"))
                        .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .setBodySource(createStaticBodyGenerator(json, Charsets.UTF_8))
                        .build(),
                createStatusResponseHandler());

        assertEquals(response.getStatusCode(), NOT_ALLOWED);

        assertNull(store.get("foo"));
    }

    private URI uriFor(String path)
    {
        return server.getBaseUrl().resolve(path);
    }
}
