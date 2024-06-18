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
import io.airlift.http.client.Request;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonModule;
import io.airlift.node.testing.TestingNodeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareDelete;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.Request.Builder.preparePut;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.json.JsonCodec.listJsonCodec;
import static io.airlift.json.JsonCodec.mapJsonCodec;
import static io.airlift.sample.PersonEvent.personAdded;
import static io.airlift.sample.PersonEvent.personRemoved;
import static io.airlift.testing.Assertions.assertEqualsIgnoreOrder;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.net.HttpURLConnection.HTTP_BAD_METHOD;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
public class TestServer
{
    private HttpClient client;
    private TestingHttpServer server;

    private PersonStore store;

    private final JsonCodec<Map<String, Object>> mapCodec = mapJsonCodec(String.class, Object.class);
    private final JsonCodec<List<Object>> listCodec = listJsonCodec(Object.class);
    private InMemoryEventClient eventClient;
    private LifeCycleManager lifeCycleManager;

    @BeforeEach
    public void setup()
    {
        Bootstrap app = new Bootstrap(
                new TestingNodeModule(),
                new InMemoryEventModule(),
                new TestingHttpServerModule(),
                new JsonModule(),
                new JaxrsModule(),
                new MainModule());

        Injector injector = app
                .doNotInitializeLogging()
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);

        server = injector.getInstance(TestingHttpServer.class);
        store = injector.getInstance(PersonStore.class);
        eventClient = injector.getInstance(InMemoryEventClient.class);

        client = new JettyHttpClient();
    }

    @AfterEach
    public void teardown()
    {
        try {
            if (lifeCycleManager != null) {
                lifeCycleManager.stop();
            }
        }
        finally {
            client.close();
        }
    }

    @Test
    public void testEmpty()
    {
        List<Object> response = client.execute(
                prepareGet().setUri(uriFor("/v1/person")).build(),
                createJsonResponseHandler(listCodec));

        assertThat(response).isEqualTo(ImmutableList.of());
    }

    @Test
    public void testGetAll()
            throws IOException
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
            throws IOException
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        URI requestUri = uriFor("/v1/person/foo");

        Map<String, Object> expected = mapCodec.fromJson(Resources.toString(Resources.getResource("single.json"), UTF_8));
        expected.put("self", requestUri.toString());

        Map<String, Object> actual = client.execute(
                prepareGet().setUri(requestUri).build(),
                createJsonResponseHandler(mapCodec));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testPut()
            throws IOException
    {
        String json = Resources.toString(Resources.getResource("single.json"), UTF_8);

        StatusResponse response = client.execute(
                preparePut()
                        .setUri(uriFor("/v1/person/foo"))
                        .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .setBodyGenerator(createStaticBodyGenerator(json, UTF_8))
                        .build(),
                createStatusResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(HTTP_CREATED);

        assertThat(store.get("foo")).isEqualTo(new Person("foo@example.com", "Mr Foo"));

        assertThat(eventClient.getEvents()).isEqualTo(ImmutableList.of(
                personAdded("foo", new Person("foo@example.com", "Mr Foo"))));
    }

    @Test
    public void testDelete()
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        StatusResponse response = client.execute(
                prepareDelete()
                        .setUri(uriFor("/v1/person/foo"))
                        .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .build(),
                createStatusResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(HTTP_NO_CONTENT);

        assertThat(store.get("foo")).isNull();

        assertThat(eventClient.getEvents()).isEqualTo(ImmutableList.of(
                personAdded("foo", new Person("foo@example.com", "Mr Foo")),
                personRemoved("foo", new Person("foo@example.com", "Mr Foo"))));
    }

    @Test
    public void testDeleteMissing()
    {
        StatusResponse response = client.execute(
                prepareDelete()
                        .setUri(uriFor("/v1/person/foo"))
                        .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .build(),
                createStatusResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(HTTP_NOT_FOUND);
    }

    @Test
    public void testPostNotAllowed()
            throws IOException
    {
        String json = Resources.toString(Resources.getResource("single.json"), UTF_8);

        StatusResponse response = client.execute(
                preparePost()
                        .setUri(uriFor("/v1/person/foo"))
                        .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .setBodyGenerator(createStaticBodyGenerator(json, UTF_8))
                        .build(),
                createStatusResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(HTTP_BAD_METHOD);

        assertThat(store.get("foo")).isNull();
    }

    @Test
    public void testOptions()
    {
        StringResponse response = client.execute(
                new Request.Builder()
                        .setMethod("OPTIONS")
                        .setUri(uriFor("/v1/person"))
                        .build(),
                createStringResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(HTTP_OK);
        assertThat(response.getHeader(CONTENT_TYPE)).isEqualTo("application/vnd.sun.wadl+xml");
        assertThat(response.getBody()).startsWith("<?xml ").contains("<application ");
    }

    private URI uriFor(String path)
    {
        return server.getBaseUrl().resolve(path);
    }
}
