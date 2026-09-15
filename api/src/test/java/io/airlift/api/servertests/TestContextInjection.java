package io.airlift.api.servertests;

import com.google.inject.Injector;
import io.airlift.api.ApiPagination;
import io.airlift.api.ApiPatch;
import io.airlift.api.ApiValidateOnly;
import io.airlift.api.TestApiContext.ContextService;
import io.airlift.api.TestApiContext.Injected;
import io.airlift.api.TestApiContext.Payload;
import io.airlift.api.binding.ApiModule;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.node.NodeModule;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.model.Parameter;
import org.glassfish.jersey.server.spi.internal.ValueParamProvider;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.Request.Builder.preparePut;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static io.airlift.json.JsonCodec.jsonCodec;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;

public class TestContextInjection
{
    @Test
    public void testApplicationValueProvider()
            throws Exception
    {
        Injector injector = new Bootstrap(
                new NodeModule(),
                new TestingHttpServerModule("context-test"),
                new JsonModule(),
                new JaxrsModule(),
                binder -> jaxrsBinder(binder).bind(ContextValueProvider.class),
                ApiModule.builder().addApi(builder -> builder.add(ContextService.class)).build())
                .setRequiredConfigurationProperties(Map.of("node.environment", "testing"))
                .quiet()
                .doNotInitializeLogging()
                .initialize();
        try (JettyHttpClient client = new JettyHttpClient("context-test", new HttpClientConfig())) {
            URI baseUri = injector.getInstance(HttpServerInfo.class).getHttpUri();
            UUID requestId = UUID.randomUUID();
            Request get = prepareGet()
                    .setUri(baseUri.resolve("/public/api/v1/payload"))
                    .setHeader("X-Request-Id", requestId.toString())
                    .build();
            assertThat(client.execute(get, createJsonResponseHandler(jsonCodec(Payload.class))))
                    .isEqualTo(new Payload(requestId + ":/public/api/v1/payload"));

            Request create = preparePost()
                    .setUri(baseUri.resolve("/public/api/v1/payload?validateOnly=true"))
                    .setHeader("X-Request-Id", requestId.toString())
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .setBodyGenerator(jsonBodyGenerator(jsonCodec(Payload.class), new Payload("created")))
                    .build();
            assertThat(client.execute(create, createJsonResponseHandler(jsonCodec(Payload.class))))
                    .isEqualTo(new Payload(requestId + ":created:true:false:17"));

            Request update = preparePut()
                    .setUri(baseUri.resolve("/public/api/v1/payload?validateOnly=true"))
                    .setHeader("X-Request-Id", requestId.toString())
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .setBodyGenerator(jsonBodyGenerator(jsonCodec(Payload.class), new Payload("updated")))
                    .build();
            assertThat(client.execute(update, createJsonResponseHandler(jsonCodec(Payload.class))))
                    .isEqualTo(new Payload(requestId + ":updated:true:0"));
        }
        finally {
            injector.getInstance(LifeCycleManager.class).stop();
        }
    }

    public static class ContextValueProvider
            implements ValueParamProvider
    {
        @Override
        public Function<ContainerRequest, ?> getValueProvider(Parameter parameter)
        {
            Injected annotation = parameter.getAnnotation(Injected.class);
            if (annotation == null) {
                return null;
            }
            if (parameter.getRawType() == UUID.class) {
                return request -> UUID.fromString(request.getHeaderString(annotation.value()));
            }
            if (parameter.getRawType() == ApiValidateOnly.class) {
                return _ -> new ApiValidateOnly(false);
            }
            if (parameter.getRawType() == ApiPagination.class) {
                return _ -> new ApiPagination(Optional.empty(), 17, Optional.empty());
            }
            if (parameter.getRawType() == ApiPatch.class) {
                return _ -> new ApiPatch<>(Map.of());
            }
            throw new AssertionError("Unexpected context parameter: " + parameter);
        }

        @Override
        public PriorityType getPriority()
        {
            return Priority.NORMAL;
        }
    }
}
