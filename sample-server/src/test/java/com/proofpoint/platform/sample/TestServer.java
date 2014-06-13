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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import static com.proofpoint.json.JsonCodec.listJsonCodec;
import static com.proofpoint.json.JsonCodec.mapJsonCodec;
import static com.proofpoint.platform.sample.PersonEvent.personAdded;
import static com.proofpoint.platform.sample.PersonEvent.personRemoved;
import static com.proofpoint.testing.Assertions.assertEqualsIgnoreOrder;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestServer
{
    private static final int NOT_ALLOWED = 405;

    private HttpClient client;
    private TestingHttpServer server;

    private PersonStore store;

    private final JsonCodec<Map<String, Object>> mapCodec = mapJsonCodec(String.class, Object.class);
    private final JsonCodec<List<Object>> listCodec = listJsonCodec(Object.class);
    private InMemoryEventClient eventClient;
    private LifeCycleManager lifeCycleManager;

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
                );

        Injector injector = app
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);

        server = injector.getInstance(TestingHttpServer.class);
        store = injector.getInstance(PersonStore.class);
        eventClient = (InMemoryEventClient) injector.getInstance(EventClient.class);

        client = new JettyHttpClient();
    }

    @AfterMethod
    public void teardown()
            throws Exception
    {
        try {
            if (lifeCycleManager != null) {
                lifeCycleManager.stop();
            }
        }
        finally {
            Closeables.closeQuietly(client);
        }
    }

    @Test
    public void testEmpty()
            throws Exception
    {
        List<Object> response = client.execute(
                prepareGet().setUri(uriFor("/v1/person")).build(),
                createJsonResponseHandler(listCodec));

        assertEquals(response, Collections.emptyList());
    }

    @Test
    public void testGetAll()
            throws IOException, ExecutionException, InterruptedException
    {
        store.put("bar", new Person("bar@example.com", "Mr Bar"));
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        List<Object> expected = new ArrayList<>();
        expected.add(ImmutableMap.of("self", uriFor("/v1/person/foo").toString(), "name", "Mr Foo", "email", "foo@example.com"));
        expected.add(ImmutableMap.of("self", uriFor("/v1/person/bar").toString(), "name", "Mr Bar", "email", "bar@example.com"));

        List<Object> actual = client.execute(
                prepareGet().setUri(uriFor("/v1/person")).build(),
                createJsonResponseHandler(listCodec));
        assertEqualsIgnoreOrder(actual, expected);
    }

    @Test
    public void testGetSingle()
            throws IOException, ExecutionException, InterruptedException
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        URI requestUri = uriFor("/v1/person/foo");

        Map<String, Object> expected = mapCodec.fromJson(Resources.toString(Resources.getResource("single.json"), Charsets.UTF_8));
        expected.put("self", requestUri.toString());

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
                        .setBodyGenerator(createStaticBodyGenerator(json, Charsets.UTF_8))
                        .build(),
                createStatusResponseHandler());

        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.CREATED.getStatusCode());

        assertEquals(store.get("foo"), new Person("foo@example.com", "Mr Foo"));

        assertEquals(eventClient.getEvents(), ImmutableList.of(
                personAdded("foo", new Person("foo@example.com", "Mr Foo"))
        ));
    }

    @Test
    public void testDelete()
            throws IOException, ExecutionException, InterruptedException
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        StatusResponse response = client.execute(
                prepareDelete()
                        .setUri(uriFor("/v1/person/foo"))
                        .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .build(),
                createStatusResponseHandler());

        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.NO_CONTENT.getStatusCode());

        assertNull(store.get("foo"));

        assertEquals(eventClient.getEvents(), ImmutableList.of(
                personAdded("foo", new Person("foo@example.com", "Mr Foo")),
                personRemoved("foo", new Person("foo@example.com", "Mr Foo"))
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
                        .setBodyGenerator(createStaticBodyGenerator(json, Charsets.UTF_8))
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
