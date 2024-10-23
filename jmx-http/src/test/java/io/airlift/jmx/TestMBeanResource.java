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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.airlift.http.client.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.StringResponse;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static java.lang.management.ManagementFactory.MEMORY_MXBEAN_NAME;
import static java.lang.management.ManagementFactory.RUNTIME_MXBEAN_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestMBeanResource
{
    private final MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
    private LifeCycleManager lifeCycleManager;
    private TestingHttpServer server;
    private HttpClient client;

    @BeforeAll
    public void setup()
    {
        Bootstrap app = new Bootstrap(
                new TestingNodeModule(),
                new TestingHttpServerModule(),
                new JsonModule(),
                new JaxrsModule(),
                new JmxHttpModule(),
                binder -> binder.bind(MBeanServer.class).toInstance(mbeanServer));

        Injector injector = app
                .quiet()
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingHttpServer.class);
        client = new JettyHttpClient();
    }

    @AfterAll
    public void teardown()
    {
        try (HttpClient ignored = client) {
            if (lifeCycleManager != null) {
                lifeCycleManager.stop();
            }
        }
    }

    @Test
    public void testGetHtmlPage()
    {
        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/v1/jmx")).build(),
                createStringResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertContentType(response, HTML_UTF_8);
        assertThat(response.getBody()).contains("<html>");
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
            assertThat(name.isTextual()).isTrue();
            names.add(name.asText());
        }

        assertThat(names).contains(MEMORY_MXBEAN_NAME);
        assertThat(names).contains(RUNTIME_MXBEAN_NAME);
        assertThat(names).containsExactlyInAnyOrderElementsOf(getMBeanNames());
    }

    @Test
    public void testGetMBean()
            throws Exception
    {
        for (String mbeanName : getMBeanNames()) {
            URI uri = uriBuilderFrom(uriFor("/v1/jmx/mbean"))
                    .appendPath(mbeanName)
                    .build();
            JsonNode mbean = jsonRequest(uri);

            JsonNode name = mbean.get("objectName");
            assertThat(name.isTextual()).isTrue();
            assertThat(name.asText()).isEqualTo(mbeanName);
        }
    }

    @Test
    public void testGetMBeanJsonp()
            throws Exception
    {
        for (String mbeanName : getMBeanNames()) {
            URI uri = uriBuilderFrom(uriFor("/v1/jmx/mbean"))
                    .appendPath(mbeanName)
                    .build();
            JsonNode mbean = jsonpRequest(uri);

            JsonNode name = mbean.get("objectName");
            assertThat(name.isTextual()).isTrue();
            assertThat(name.asText()).isEqualTo(mbeanName);
        }
    }

    private JsonNode jsonRequest(URI uri)
            throws IOException
    {
        Request request = prepareGet().setUri(uri).build();
        StringResponse response = client.execute(request, createStringResponseHandler());

        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(200);
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

        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(200);
        assertContentType(response, JSON_UTF_8);

        String jsonp = response.getBody().trim();
        assertThat(jsonp).startsWith("test(");
        assertThat(jsonp).endsWith(")");
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
        assertThat(MediaType.parse(contentType).is(type.withoutParameters())).as(contentType).isTrue();
    }
}
