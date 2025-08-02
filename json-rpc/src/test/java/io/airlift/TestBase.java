package io.airlift;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonModule;
import io.airlift.jsonrpc.JsonRpcModule.Builder;
import io.airlift.jsonrpc.model.JsonRpcRequest;
import io.airlift.jsonrpc.model.JsonRpcResponse;
import io.airlift.node.NodeModule;
import jakarta.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.http.client.HttpClientBinder.httpClientBinder;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;

public abstract class TestBase
{
    protected final Injector injector;
    protected final HttpClient httpClient;
    protected final URI baseUri;
    protected final ObjectMapper objectMapper;

    protected TestBase(Builder<?> rpcModuleBuilder, Module... additionalModules)
    {
        ImmutableList.Builder<Module> modules = ImmutableList.<Module>builder()
                .add(rpcModuleBuilder.withBasePath("test").build())
                .add(new NodeModule())
                .add(new TestingHttpServerModule())
                .add(new JsonModule())
                .add(new JaxrsModule())
                .add(binder -> httpClientBinder(binder).bindHttpClient("test", ForTesting.class));
        modules.addAll(Arrays.asList(additionalModules));

        ImmutableMap.Builder<String, String> serverProperties = ImmutableMap.<String, String>builder()
                .put("node.environment", "testing");

        Bootstrap app = new Bootstrap(modules.build());
        app.setRequiredConfigurationProperties(serverProperties.build());
        injector = app.quiet().doNotInitializeLogging().initialize();

        httpClient = injector.getInstance(Key.get(HttpClient.class, ForTesting.class));
        baseUri = injector.getInstance(HttpServerInfo.class).getHttpUri();
        objectMapper = injector.getInstance(ObjectMapper.class);
    }

    protected URI uri()
    {
        return baseUri.resolve("test");
    }

    protected <T> Request buildRequest(@Nullable Object id, String method, TypeToken<JsonRpcRequest<T>> type, T params)
    {
        return internalBuildRequest(id, method, type, Optional.ofNullable(params));
    }

    protected Request buildRequest(@Nullable Object id, String method)
    {
        return internalBuildRequest(id, method, new TypeToken<>() {}, Optional.empty());
    }

    protected Request buildNotification(String method)
    {
        return internalBuildRequest(null, method, new TypeToken<>() {}, Optional.empty());
    }

    protected <T> Request buildResponse(Object id, TypeToken<JsonRpcResponse<T>> type, Optional<T> maybeResult)
    {
        JsonRpcResponse<T> jsonRpcResponse = new JsonRpcResponse<>(id, Optional.empty(), maybeResult);
        Request.Builder builder = Request.Builder.preparePost()
                .setUri(uri())
                .setHeader("Content-Type", "application/json")
                .setBodyGenerator(jsonBodyGenerator(JsonCodec.jsonCodec(type), jsonRpcResponse));
        return builder.build();
    }

    protected Iterable<Map<String, String>> readEvents(InputStream inputStream)
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        return () -> new Iterator<>()
        {
            private boolean isDone;
            private Map<String, String> current = ImmutableMap.of();

            {
                readNext();
            }

            @Override
            public boolean hasNext()
            {
                return !current.isEmpty();
            }

            @Override
            public Map<String, String> next()
            {
                Map<String, String> result = current;
                readNext();
                return result;
            }

            private void readNext()
            {
                current = new HashMap<>();
                if (isDone) {
                    return;
                }

                try {
                    while (true) {
                        String line = reader.readLine();

                        if (line == null) {
                            isDone = true;
                            break;
                        }

                        if (line.isEmpty()) {
                            break;
                        }

                        List<String> parts = Splitter.on(':').limit(2).trimResults().splitToList(line);
                        switch (parts.size()) {
                            case 1 -> current.put(parts.getFirst(), "");
                            case 2 -> current.put(parts.getFirst(), parts.getLast());
                            default -> {}   // do nothing
                        }
                    }
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }

    private <T> Request internalBuildRequest(@Nullable Object id, String method, TypeToken<JsonRpcRequest<T>> type, Optional<T> maybeParams)
    {
        JsonRpcRequest<T> jsonRpcRequest = maybeParams.map(params -> (id == null) ? JsonRpcRequest.<T>buildNotification(method) : JsonRpcRequest.buildRequest(id, method, params))
                .orElseGet(() -> JsonRpcRequest.buildRequest(id, method));
        Request.Builder builder = Request.Builder.preparePost()
                .setUri(uri())
                .setHeader("Content-Type", "application/json")
                .setBodyGenerator(jsonBodyGenerator(JsonCodec.jsonCodec(type), jsonRpcRequest));
        return builder.build();
    }
}
