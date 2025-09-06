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
package io.airlift.jaxrs.programmatic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.BindingAnnotation;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
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
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.annotation.Retention;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class TestProgrammaticResource
{
    public String getResult()
    {
        return "dummy";
    }

    @Test
    public void testProgrammaticResourceBinding()
            throws NoSuchMethodException
    {
        Resource.Builder builder = Resource.builder();
        ResourceMethod.Builder method = builder.path("/foo/bar").addMethod("GET");
        Method getResultMethod = getClass().getMethod("getResult");
        method.handledBy(this, getResultMethod);
        Resource resource = builder.build();

        Module module = binder -> jaxrsBinder(binder).bind(Resource.class, () -> resource);
        Injector injector = new Bootstrap(
                module,
                new JaxrsModule(),
                new JaxrsModule(Secondary.class),
                new JsonModule())
                .quiet()
                .initialize();
        ResourceConfig resourceConfig = injector.getInstance(ResourceConfig.class);

        List<ResourceMethod> foundMethods = resourceConfig.getResources().stream()
                .filter(r -> r.getPath().equals("/foo/bar"))
                .flatMap(r -> r.getAllMethods().stream())
                .collect(toImmutableList());
        assertThat(foundMethods.size()).isEqualTo(1);
        ResourceMethod foundMethod = foundMethods.get(0);
        assertThat(foundMethod.getInvocable().getHandlingMethod()).isEqualTo(getResultMethod);
        assertThat(foundMethod.getInvocable().getHandler().getHandlerClass()).isEqualTo(getClass());

        ResourceConfig secondaryConfig = injector.getInstance(Key.get(ResourceConfig.class, Secondary.class));
        assertThat(secondaryConfig.getResources()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testJsonParsingExceptionMapper(boolean useMapper)
            throws JsonProcessingException
    {
        Injector injector = new Bootstrap(
                binder -> {
                    JaxrsBinder jaxrsBinder = jaxrsBinder(binder);
                    if (!useMapper) {
                        jaxrsBinder.disableJsonExceptionMapper();
                    }
                    jaxrsBinder.bind(ResourceWithBadJson.class);
                },
                new TestingNodeModule(),
                new JaxrsModule(),
                new JsonModule(),
                new TestingHttpServerModule())
                .quiet()
                .doNotInitializeLogging()
                .initialize();

        URI baseUri = injector.getInstance(TestingHttpServer.class).getBaseUrl();
        try (JettyHttpClient client = new JettyHttpClient(new HttpClientConfig())) {
            Request request = preparePost().setUri(baseUri)
                    .setHeader("Content-Type", "application/json")
                    .setBodyGenerator(createStaticBodyGenerator("this ain't json", UTF_8))
                    .build();

            StringResponse response = client.execute(request, createStringResponseHandler());

            if (useMapper) {
                assertThat(response.getStatusCode()).isEqualTo(400);
                assertThat(response.getHeader(CONTENT_TYPE)).isEqualTo(APPLICATION_JSON_TYPE.toString());
                JsonError error = JsonError.codec().fromJson(response.getBody());
                assertThat(error.message()).startsWith("Unrecognized token 'this'");
                assertThat(error.code()).isEqualTo("JSON_PARSING_ERROR");
            }
            else {
                assertThat(response.getStatusCode()).isEqualTo(500);
            }
        }

        injector.getInstance(LifeCycleManager.class).stop();
    }

    @BindingAnnotation
    @Retention(RUNTIME)
    private @interface Secondary
    {
    }
}
