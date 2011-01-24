package com.proofpoint.platform.sample;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.jersey.JerseyModule;
import com.proofpoint.jetty.JettyModule;
import com.proofpoint.net.NetUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jetty.server.Server;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestServer
{
    private static final int NOT_ALLOWED = 405;

    private AsyncHttpClient client;
    private Server server;
    private File tempDir;
    private int port;

    private PersonStore store;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        tempDir = Files.createTempDir().getCanonicalFile(); // getCanonicalFile needed to get around Issue 365 (http://code.google.com/p/guava-libraries/issues/detail?id=365)
        port = NetUtils.findUnusedPort();

        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("jetty.http.port", String.valueOf(port))
                .put("jetty.log.path", new File(tempDir, "jetty.log").getAbsolutePath())
                .build();

        // TODO: wrap all this stuff in a TestBootstrap class
        ConfigurationFactory configFactory = new ConfigurationFactory(properties);
        Injector injector = Guice.createInjector(new JettyModule(),
                                                 new JerseyModule(),
                                                 new MainModule(),
                                                 new ConfigurationModule(configFactory));

        server = injector.getInstance(Server.class);
        store = injector.getInstance(PersonStore.class);

        server.start();

        client = new AsyncHttpClient();
    }

    @AfterMethod
    public void teardown()
            throws Exception
    {
        try {
            if (server != null) {
                server.stop();
            }

            if (client != null) {
                client.close();
            }
        }
        finally {
            Files.deleteRecursively(tempDir);
        }
    }

    @Test
    public void testEmpty()
            throws Exception
    {
        Response response = client.prepareGet(urlFor("/v1/person")).execute().get();

        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);
        assertEquals(fromJson(response.getResponseBody(), List.class), fromJson("[]", List.class));
    }

    @Test
    public void testGetAll()
            throws IOException, ExecutionException, InterruptedException
    {
        store.put("bar", new Person("bar@example.com", "Mr Bar"));
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        Response response = client.prepareGet(urlFor("/v1/person")).execute().get();

        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        List<?> expected = fromJson(Resources.toString(Resources.getResource("list.json"), Charsets.UTF_8), List.class);
        List<?> actual = fromJson(response.getResponseBody(), List.class);

        assertEquals(newHashSet(actual), newHashSet(expected));
    }

    @Test
    public void testGetSingle()
            throws IOException, ExecutionException, InterruptedException
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        Response response = client.prepareGet(urlFor("/v1/person/foo")).execute().get();

        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        Map<?, ?> expected = fromJson(Resources.toString(Resources.getResource("single.json"), Charsets.UTF_8), Map.class);
        Map<?, ?> actual = fromJson(response.getResponseBody(), Map.class);

        assertEquals(actual, expected);
    }

    @Test
    public void testPut()
            throws IOException, ExecutionException, InterruptedException
    {
        String json = Resources.toString(Resources.getResource("single.json"), Charsets.UTF_8);
        Response response = client.preparePut(urlFor("/v1/person/foo"))
                .setHeader("Content-Type", MediaType.APPLICATION_JSON)
                .setBody(json)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());

        assertEquals(store.get("foo"), new Person("foo@example.com", "Mr Foo"));
    }

    @Test
    public void testDelete()
            throws IOException, ExecutionException, InterruptedException
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        Response response = client.prepareDelete(urlFor("/v1/person/foo"))
                .setHeader("Content-Type", MediaType.APPLICATION_JSON)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());

        assertNull(store.get("foo"));
    }

    @Test
    public void testDeleteMissing()
            throws IOException, ExecutionException, InterruptedException
    {
        Response response = client.prepareDelete(urlFor("/v1/person/foo"))
                .setHeader("Content-Type", MediaType.APPLICATION_JSON)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
    }

    @Test
    public void testPostNotAllowed()
            throws IOException, ExecutionException, InterruptedException
    {
        String json = Resources.toString(Resources.getResource("single.json"), Charsets.UTF_8);
        Response response = client.preparePost(urlFor("/v1/person/foo"))
                .setHeader("Content-Type", MediaType.APPLICATION_JSON)
                .setBody(json)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), NOT_ALLOWED);

        assertNull(store.get("foo"));
    }

    private String urlFor(String path)
    {
        return format("http://localhost:%d%s", port, path);
    }

    private static <T> T fromJson(String json, Class<T> clazz)
            throws IOException
    {
        // TODO: use JsonCodec or similar
        ObjectMapper mapper = new ObjectMapper();
        return clazz.cast(mapper.readValue(json, Object.class));
    }
}
