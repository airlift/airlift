package io.airlift.api.servertests;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closer;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import io.airlift.api.binding.ApiModule;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.node.NodeModule;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class ServerTestBase
{
    protected final Closer closer = Closer.create();
    protected final URI baseUri;
    protected final JettyHttpClient httpClient = new JettyHttpClient("testing", new HttpClientConfig());
    protected final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    protected ObjectMapper objectMapper;

    public ServerTestBase()
    {
        this(Optional.empty(), ignore -> {});
    }

    protected ServerTestBase(Consumer<ApiModule.Builder> builderConsumer)
    {
        this(Optional.empty(), builderConsumer);
    }

    protected ServerTestBase(Class<?> serviceClass)
    {
        this(serviceClass, ignore -> {});
    }

    protected ServerTestBase(Class<?> serviceClass, Consumer<ApiModule.Builder> builderConsumer)
    {
        this(Optional.of(serviceClass), builderConsumer);
    }

    private ServerTestBase(Optional<Class<?>> maybeServiceClass, Consumer<ApiModule.Builder> builderConsumer)
    {
        ImmutableList.Builder<Module> modules = ImmutableList.<Module>builder()
                .add(new NodeModule())
                .add(new TestingHttpServerModule(getClass().getName()))
                .add(binder -> binder.bind(new TypeLiteral<Map<String, AtomicInteger>>() {}).toInstance(counters))
                .add(new JsonModule())
                .add(new JaxrsModule());

        ApiModule.Builder builder = ApiModule.builder()
                .addApi(apiBuilder -> maybeServiceClass.ifPresent(apiBuilder::add));
        builderConsumer.accept(builder);
        Module apiModule = builder.build();
        modules.add(apiModule);

        ImmutableMap.Builder<String, String> serverProperties = ImmutableMap.<String, String>builder()
                .put("node.environment", "testing");

        Bootstrap app = new Bootstrap(modules.build());
        Injector injector = app
                .setRequiredConfigurationProperties(serverProperties.build())
                .quiet()
                .doNotInitializeLogging()
                .initialize();
        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        closer.register(lifeCycleManager::stop);
        HttpServerInfo httpServerInfo = injector.getInstance(HttpServerInfo.class);
        baseUri = UriBuilder.fromUri(httpServerInfo.getHttpsUri() != null ? httpServerInfo.getHttpsUri() : httpServerInfo.getHttpUri())
                .host("localhost")
                .build();

        objectMapper = injector.getInstance(ObjectMapper.class);
    }

    @AfterAll
    public void tearDown()
            throws IOException
    {
        closer.close();
    }
}
