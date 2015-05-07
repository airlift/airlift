package io.airlift.jmx;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.net.MediaType;
import com.google.inject.Injector;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.json.ObjectMapperProvider;
import io.airlift.node.testing.TestingNodeModule;
import io.airlift.testing.Closeables;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.airlift.http.client.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.StringResponse;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.testing.Assertions.assertContains;
import static io.airlift.testing.Assertions.assertEqualsIgnoreOrder;
import static java.lang.management.ManagementFactory.MEMORY_MXBEAN_NAME;
import static java.lang.management.ManagementFactory.RUNTIME_MXBEAN_NAME;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestMBeanResource
{
    private final MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
    private LifeCycleManager lifeCycleManager;
    private TestingHttpServer server;
    private HttpClient client;

    @BeforeClass
    public void setup()
            throws Exception
    {
        Bootstrap app = new Bootstrap(
                new TestingNodeModule(),
                new TestingHttpServerModule(),
                new JsonModule(),
                new JaxrsModule(true),
                new JmxHttpModule(),
                binder -> binder.bind(MBeanServer.class).toInstance(mbeanServer));

        Injector injector = app
                .quiet()
                .strictConfig()
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingHttpServer.class);
        client = new JettyHttpClient();
    }

    @AfterClass(alwaysRun = true)
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

    @DataProvider(name = "mbeanNames")
    public Iterator<Object[]> createMBeanNames()
    {
        List<String> names = getMBeanNames();
        ImmutableList.Builder<Object[]> data = ImmutableList.builder();
        for (String name : names) {
            data.add(new Object[] {name});
        }
        return data.build().iterator();
    }

    @Test
    public void testGetHtmlPage()
            throws Exception
    {
        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/v1/jmx")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200);
        assertContentType(response, HTML_UTF_8);
        assertContains(response.getBody(), "<html>");
    }

    @Test
    public void testGetMBeans()
            throws Exception
    {
        assertMBeansResponse(jsonRequest(uriFor("/v1/jmx/mbean")));
    }

    @Test
    public void testGetMBeansJsonp()
            throws Exception
    {
        assertMBeansResponse(jsonpRequest(uriFor("/v1/jmx/mbean")));
    }

    private void assertMBeansResponse(JsonNode mbeans)
    {
        List<String> names = new ArrayList<>();
        for (JsonNode mbean : mbeans) {
            JsonNode name = mbean.get("objectName");
            assertTrue(name.isTextual());
            names.add(name.asText());
        }

        assertTrue(names.contains(MEMORY_MXBEAN_NAME));
        assertTrue(names.contains(RUNTIME_MXBEAN_NAME));
        assertEqualsIgnoreOrder(names, getMBeanNames());
    }

    @Test(dataProvider = "mbeanNames")
    public void testGetMBean(String mbeanName)
            throws Exception
    {
        URI uri = uriBuilderFrom(uriFor("/v1/jmx/mbean"))
                .appendPath(mbeanName)
                .build();
        JsonNode mbean = jsonRequest(uri);

        JsonNode name = mbean.get("objectName");
        assertTrue(name.isTextual());
        assertEquals(name.asText(), mbeanName);
    }

    @Test(dataProvider = "mbeanNames")
    public void testGetMBeanJsonp(String mbeanName)
            throws Exception
    {
        URI uri = uriBuilderFrom(uriFor("/v1/jmx/mbean"))
                .appendPath(mbeanName)
                .build();
        JsonNode mbean = jsonpRequest(uri);

        JsonNode name = mbean.get("objectName");
        assertTrue(name.isTextual());
        assertEquals(name.asText(), mbeanName);
    }

    private JsonNode jsonRequest(URI uri)
            throws IOException
    {
        Request request = prepareGet().setUri(uri).build();
        StringResponse response = client.execute(request, createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200, response.getBody());
        assertContentType(response, JSON_UTF_8);

        return new ObjectMapperProvider().get().readTree(response.getBody());
    }

    private JsonNode jsonpRequest(URI uri)
            throws IOException
    {
        uri = uriBuilderFrom(uri)
                .addParameter("jsonp", "test")
                .build();
        Request request = prepareGet().setUri(uri).build();
        StringResponse response = client.execute(request, createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200, response.getBody());
        assertContentType(response, JSON_UTF_8);

        String jsonp = response.getBody().trim();
        assertTrue(jsonp.startsWith("test("), jsonp);
        assertTrue(jsonp.endsWith(")"), jsonp);
        jsonp = jsonp.substring(5, jsonp.length() - 1);

        return new ObjectMapperProvider().get().readTree(jsonp);
    }

    private URI uriFor(String path)
    {
        return server.getBaseUrl().resolve(path);
    }

    private List<String> getMBeanNames()
    {
        ImmutableList.Builder<String> list = ImmutableList.builder();
        for (ObjectName objectName : mbeanServer.queryNames(ObjectName.WILDCARD, null)) {
            list.add(objectName.toString());
        }
        return list.build();
    }

    private static void assertContentType(StringResponse response, MediaType type)
    {
        String contentType = response.getHeader(CONTENT_TYPE);
        assertTrue(MediaType.parse(contentType).is(type.withoutParameters()), contentType);
    }
}
