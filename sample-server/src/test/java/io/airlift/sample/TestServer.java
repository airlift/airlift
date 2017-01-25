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
package io.airlift.sample;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.inject.Injector;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.event.client.InMemoryEventClient;
import io.airlift.event.client.InMemoryEventModule;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonModule;
import io.airlift.node.testing.TestingNodeModule;
import io.airlift.testing.Closeables;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareDelete;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.Request.Builder.preparePut;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.json.JsonCodec.listJsonCodec;
import static io.airlift.json.JsonCodec.mapJsonCodec;
import static io.airlift.sample.PersonEvent.personAdded;
import static io.airlift.sample.PersonEvent.personRemoved;
import static io.airlift.testing.Assertions.assertEqualsIgnoreOrder;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

@Test(singleThreaded = true)
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
        Bootstrap app = new Bootstrap(
                new TestingNodeModule(),
                new InMemoryEventModule(),
                new TestingHttpServerModule(),
                new JsonModule(),
                new JaxrsModule(true),
                new MainModule());

        Injector injector = app
                .strictConfig()
                .doNotInitializeLogging()
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);

        server = injector.getInstance(TestingHttpServer.class);
        store = injector.getInstance(PersonStore.class);
        eventClient = injector.getInstance(InMemoryEventClient.class);

        client = new JettyHttpClient();
    }

    @AfterMethod(alwaysRun = true)
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

        assertEquals(response, Collections.<Object>emptyList());
    }

    @Test
    public void testGetAll()
            throws IOException, ExecutionException, InterruptedException
    {
        store.put("bar", new Person("bar@example.com", "Mr Bar"));
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        List<Object> expected = listCodec.fromJson(Resources.toString(Resources.getResource("list.json"), UTF_8));

        List<Object> actual = client.execute(
                prepareGet().setUri(uriFor("/v1/person")).build(),
                createJsonResponseHandler(listCodec));

        assertEqualsIgnoreOrder(expected, actual);
    }

    @Test
    public void testGetSingle()
            throws IOException, ExecutionException, InterruptedException
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        URI requestUri = uriFor("/v1/person/foo");

        Map<String, Object> expected = mapCodec.fromJson(Resources.toString(Resources.getResource("single.json"), UTF_8));
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
        String json = Resources.toString(Resources.getResource("single.json"), UTF_8);

        StatusResponse response = client.execute(
                preparePut()
                        .setUri(uriFor("/v1/person/foo"))
                        .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .setBodyGenerator(createStaticBodyGenerator(json, UTF_8))
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
        String json = Resources.toString(Resources.getResource("single.json"), UTF_8);

        StatusResponse response = client.execute(
                preparePost()
                        .setUri(uriFor("/v1/person/foo"))
                        .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .setBodyGenerator(createStaticBodyGenerator(json, UTF_8))
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
