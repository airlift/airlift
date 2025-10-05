package io.airlift.jaxrs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Injector;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.json.JsonModule;
import io.airlift.node.testing.TestingNodeModule;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.URI;

import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static io.airlift.json.JsonCodec.jsonCodec;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestThrowingResource
{
    @Path("/throwing")
    public static class ThrowingResource
    {
        @GET
        public ThrowingResult test1()
        {
            return new ThrowingResult();
        }

        public static class ThrowingResult
        {
            @JsonProperty
            public String getId()
            {
                return "dummy-id";
            }

            @JsonProperty
            public String getThrowingField()
            {
                throw new RuntimeException("Intentional exception from throwingField");
            }
        }
    }

    @Test
    public void testSerializationFailureIsSurfaced()
    {
        Injector injector = new Bootstrap(
                binder -> jaxrsBinder(binder).bind(ThrowingResource.class),
                new TestingNodeModule(),
                new TestingHttpServerModule(getClass().getName()),
                new JaxrsModule(),
                new JsonModule())
                .quiet()
                .initialize();
        try {
            URI baseUri = injector.getInstance(TestingHttpServer.class).getBaseUrl();
            try (JettyHttpClient client = new JettyHttpClient(new HttpClientConfig())) {
                Request request = prepareGet()
                        .setUri(baseUri.resolve("/throwing"))
                        .build();
                assertThatThrownBy(() -> client.execute(request, createJsonResponseHandler(jsonCodec(JsonNode.class))))
                        .hasMessageMatching(
                                """
                                Unable to create class tools.jackson.databind.JsonNode from JSON response: <\\{("id":"dummy-id")?>""")
                        .hasStackTraceContaining("Unexpected end-of-input: expected close marker for Object");
            }
        }
        finally {
            injector.getInstance(LifeCycleManager.class).stop();
        }
    }
}
