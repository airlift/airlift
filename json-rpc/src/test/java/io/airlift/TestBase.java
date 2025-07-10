package io.airlift;

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
import io.airlift.node.NodeModule;
import jakarta.annotation.Nullable;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

import static io.airlift.http.client.HttpClientBinder.httpClientBinder;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;

public abstract class TestBase
{
    protected final Injector injector;
    protected final HttpClient httpClient;
    protected final URI baseUri;

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
