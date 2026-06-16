/*
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
package io.airlift.jaxrs.programmatic;

import com.google.inject.Injector;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsBinder;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.jaxrs.JsonError;
import io.airlift.json.JsonModule;
import io.airlift.node.testing.TestingNodeModule;
import io.airlift.units.DataSize;
import io.airlift.yaml.YamlModule;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.function.Consumer;

import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static io.airlift.units.DataSize.Unit.KILOBYTE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class TestPayloadSizeLimit
{
    @Path("/")
    public static class Resource
    {
        public record Payload(String value) {}

        @POST
        @Path("/json")
        @Consumes("application/json")
        public String takeJson(Payload payload)
        {
            return payload.value();
        }

        @POST
        @Path("/yaml")
        @Consumes("application/yaml")
        public String takeYaml(Payload payload)
        {
            return payload.value();
        }
    }

    @Test
    public void testJsonOverLimitReturns413()
    {
        try (Harness harness = new Harness(jaxrsBinder -> jaxrsBinder.withJsonMaxPayloadSize(DataSize.of(1, KILOBYTE)))) {
            String body = "{\"value\":\"%s\"}".formatted("x".repeat(2048));
            StringResponse response = harness.post("/json", "application/json", body);

            assertThat(response.getStatusCode()).isEqualTo(413);
            assertThat(response.getHeader(CONTENT_TYPE)).hasValue(APPLICATION_JSON_TYPE.toString());
            JsonError error = JsonError.codec().fromJson(response.getBody());
            assertThat(error.code()).isEqualTo("PAYLOAD_TOO_LARGE");
        }
    }

    @Test
    public void testYamlOverLimitReturns413()
    {
        try (Harness harness = new Harness(jaxrsBinder -> jaxrsBinder.withYamlMaxPayloadSize(DataSize.of(1, KILOBYTE)))) {
            String body = "value: %s\n".formatted("x".repeat(2048));
            StringResponse response = harness.post("/yaml", "application/yaml", body);

            assertThat(response.getStatusCode()).isEqualTo(413);
            assertThat(response.getHeader(CONTENT_TYPE)).hasValue(APPLICATION_JSON_TYPE.toString());
            JsonError error = JsonError.codec().fromJson(response.getBody());
            assertThat(error.code()).isEqualTo("PAYLOAD_TOO_LARGE");
        }
    }

    @Test
    public void testJsonUnderLimitSucceeds()
    {
        try (Harness harness = new Harness(jaxrsBinder -> jaxrsBinder.withJsonMaxPayloadSize(DataSize.of(1, KILOBYTE)))) {
            StringResponse response = harness.post("/json", "application/json", "{\"value\":\"small\"}");
            assertThat(response.getStatusCode()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo("small");
        }
    }

    @Test
    public void testYamlUnderLimitSucceeds()
    {
        try (Harness harness = new Harness(jaxrsBinder -> jaxrsBinder.withYamlMaxPayloadSize(DataSize.of(1, KILOBYTE)))) {
            StringResponse response = harness.post("/yaml", "application/yaml", "value: small\n");
            assertThat(response.getStatusCode()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo("small");
        }
    }

    @Test
    public void testDefaultIsUnbounded()
    {
        // No limit configured. Arbitrarily large JSON payload accepted.
        try (Harness harness = new Harness(_ -> {})) {
            String body = "{\"value\":\"%s\"}".formatted("x".repeat(64 * 1024));
            StringResponse response = harness.post("/json", "application/json", body);
            assertThat(response.getStatusCode()).isEqualTo(200);
        }
    }

    private static final class Harness
            implements AutoCloseable
    {
        private final Injector injector;
        private final URI baseUri;
        private final JettyHttpClient client;

        Harness(Consumer<JaxrsBinder> configure)
        {
            try {
                injector = new Bootstrap(
                        binder -> {
                            JaxrsBinder jaxrsBinder = jaxrsBinder(binder);
                            jaxrsBinder.bind(Resource.class);
                            configure.accept(jaxrsBinder);
                        },
                        new TestingNodeModule(),
                        new JaxrsModule(),
                        new JsonModule(),
                        new YamlModule(),
                        new TestingHttpServerModule(getClass().getName()))
                        .quiet()
                        .doNotInitializeLogging()
                        .initialize();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
            baseUri = injector.getInstance(TestingHttpServer.class).getBaseUrl();
            client = new JettyHttpClient(new HttpClientConfig());
        }

        StringResponse post(String path, String contentType, String body)
        {
            Request request = preparePost()
                    .setUri(baseUri.resolve(path))
                    .setHeader(CONTENT_TYPE, contentType)
                    .setBodyGenerator(createStaticBodyGenerator(body, UTF_8))
                    .build();
            return client.execute(request, createStringResponseHandler());
        }

        @Override
        public void close()
        {
            client.close();
            injector.getInstance(LifeCycleManager.class).stop();
        }
    }
}
