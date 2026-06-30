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
import io.airlift.yaml.YamlModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static io.airlift.http.client.HeaderNames.ACCEPT;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class TestYamlResource
{
    @ParameterizedTest
    @ValueSource(strings = {"application/yaml", "application/x-yaml", "text/yaml", "text/x-yaml"})
    public void testYamlConsumesAllAliases(String contentType)
    {
        try (Harness harness = new Harness(true)) {
            Request request = preparePost().setUri(harness.baseUri.resolve("/yaml"))
                    .setHeader(CONTENT_TYPE, contentType)
                    .setBodyGenerator(createStaticBodyGenerator("name: dain\n", UTF_8))
                    .build();

            StringResponse response = harness.client.execute(request, createStringResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo("dain");
        }
    }

    @Test
    public void testJsonOnlyResourceRejectsYaml()
    {
        try (Harness harness = new Harness(true)) {
            Request request = preparePost().setUri(harness.baseUri.resolve("/jsononly"))
                    .setHeader(CONTENT_TYPE, "application/yaml")
                    .setBodyGenerator(createStaticBodyGenerator("name: dain\n", UTF_8))
                    .build();

            StringResponse response = harness.client.execute(request, createStringResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(415);
        }
    }

    @Test
    public void testYamlParsingErrorReturnsJsonContentType()
    {
        try (Harness harness = new Harness(true)) {
            Request request = preparePost().setUri(harness.baseUri.resolve("/badyaml"))
                    .setHeader(CONTENT_TYPE, "application/yaml")
                    .setBodyGenerator(createStaticBodyGenerator("name:\n  unfinished:", UTF_8))
                    .build();

            StringResponse response = harness.client.execute(request, createStringResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(400);
            assertThat(response.getHeader(CONTENT_TYPE)).hasValue(APPLICATION_JSON_TYPE.toString());
            JsonError error = JsonError.codec().fromJson(response.getBody());
            assertThat(error.code()).isEqualTo("YAML_PARSING_ERROR");
        }
    }

    @Test
    public void testYamlParsingExceptionMapperCanBeDisabled()
    {
        try (Harness harness = new Harness(false)) {
            Request request = preparePost().setUri(harness.baseUri.resolve("/badyaml"))
                    .setHeader(CONTENT_TYPE, "application/yaml")
                    .setBodyGenerator(createStaticBodyGenerator("name:\n  unfinished:", UTF_8))
                    .build();

            StringResponse response = harness.client.execute(request, createStringResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(500);
        }
    }

    @Test
    public void testAcceptHeaderChoosesYaml()
    {
        try (Harness harness = new Harness(true)) {
            Request request = Request.Builder.prepareGet().setUri(harness.baseUri.resolve("/both"))
                    .setHeader(ACCEPT, "application/yaml")
                    .build();

            StringResponse response = harness.client.execute(request, createStringResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(200);
            assertThat(response.getHeader(CONTENT_TYPE)).hasValueSatisfying(value -> assertThat(value).startsWith("application/yaml"));
            assertThat(response.getBody()).contains("name: dain");
        }
    }

    @Test
    public void testAcceptHeaderChoosesJson()
    {
        try (Harness harness = new Harness(true)) {
            Request request = Request.Builder.prepareGet().setUri(harness.baseUri.resolve("/both"))
                    .setHeader(ACCEPT, "application/json")
                    .build();

            StringResponse response = harness.client.execute(request, createStringResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(200);
            assertThat(response.getHeader(CONTENT_TYPE)).hasValueSatisfying(value -> assertThat(value).startsWith("application/json"));
            assertThat(response.getBody()).contains("\"name\"").contains("\"dain\"");
        }
    }

    private static final class Harness
            implements AutoCloseable
    {
        private final Injector injector;
        final URI baseUri;
        final JettyHttpClient client;

        Harness(boolean yamlMapperEnabled)
        {
            try {
                injector = new Bootstrap(
                        binder -> {
                            JaxrsBinder jaxrsBinder = jaxrsBinder(binder);
                            if (!yamlMapperEnabled) {
                                jaxrsBinder.disableYamlExceptionMapper();
                            }
                            jaxrsBinder.bind(ResourceWithYaml.class);
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

        @Override
        public void close()
        {
            client.close();
            injector.getInstance(LifeCycleManager.class).stop();
        }
    }
}
