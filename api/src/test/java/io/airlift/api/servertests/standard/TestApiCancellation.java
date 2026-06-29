package io.airlift.api.servertests.standard;

import com.google.inject.multibindings.Multibinder;
import io.airlift.api.ApiCancellation;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;
import io.airlift.api.binding.TestApiCancellationTriggerFilter;
import io.airlift.api.servertests.ServerTestBase;
import io.airlift.http.client.Request;
import io.airlift.json.JsonCodec;
import jakarta.servlet.Filter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.server.HttpServerBinder.httpServerBinder;
import static io.airlift.http.server.ServerFeature.REQUEST_CANCELLATION;
import static io.airlift.json.JsonCodec.jsonCodec;
import static org.assertj.core.api.Assertions.assertThat;

public class TestApiCancellation
        extends ServerTestBase
{
    private static final String CANCELLABLE_REQUEST_THREAD_NAME = "api-cancellable-request-test";
    private static final String CANCELLATION_LISTENER_THREAD_NAME = "api-cancellation-listener-test";
    private static final ExecutorService CANCELLABLE_REQUEST_EXECUTOR = Executors.newSingleThreadExecutor(daemonThreadFactory(CANCELLABLE_REQUEST_THREAD_NAME));
    private static final ExecutorService CANCELLATION_LISTENER_EXECUTOR = Executors.newSingleThreadExecutor(daemonThreadFactory(CANCELLATION_LISTENER_THREAD_NAME));
    private static final JsonCodec<Thing> THING_JSON_CODEC = jsonCodec(Thing.class);
    private static final AtomicReference<CompletableFuture<String>> cancellationListenerThread = new AtomicReference<>();

    public TestApiCancellation()
    {
        super(CancellableService.class,
                builder -> builder.withCancellableRequestExecutor(CANCELLABLE_REQUEST_EXECUTOR),
                binder -> {
                    Multibinder.newSetBinder(binder, Filter.class).addBinding().to(TestApiCancellationTriggerFilter.class);
                    httpServerBinder(binder).withFeature(REQUEST_CANCELLATION);
                });
    }

    @AfterAll
    public void shutdownExecutors()
    {
        CANCELLABLE_REQUEST_EXECUTOR.shutdownNow();
        CANCELLATION_LISTENER_EXECUTOR.shutdownNow();
    }

    @Test
    public void testCancellableApiRunsOnCancellableRequestExecutor()
    {
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/thing/12345").build();
        Request request = prepareGet().setUri(uri).build();

        Thing thing = httpClient.execute(request, createJsonResponseHandler(THING_JSON_CODEC));

        assertThat(thing.name()).isEqualTo(CANCELLABLE_REQUEST_THREAD_NAME);
        assertThat(thing.qty()).isEqualTo(0);
    }

    @Test
    public void testCancellationListenerRunsWhenServletCancelsRequest()
            throws Exception
    {
        CompletableFuture<String> listenerThread = new CompletableFuture<>();
        cancellationListenerThread.set(listenerThread);
        try {
            URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/thing/cancel").build();
            Request request = prepareGet().setUri(uri).build();

            Thing thing = httpClient.execute(request, createJsonResponseHandler(THING_JSON_CODEC));

            assertThat(thing.name()).isEqualTo(CANCELLABLE_REQUEST_THREAD_NAME);
            assertThat(listenerThread.get(5, TimeUnit.SECONDS)).isEqualTo(CANCELLATION_LISTENER_THREAD_NAME);
        }
        finally {
            cancellationListenerThread.set(null);
        }
    }

    @SuppressWarnings("unused")
    @ApiService(type = ServiceType.class, name = "cancellable", description = "Cancellable API test")
    public static class CancellableService
    {
        @ApiGet(description = "Get cancellable thing")
        public Thing getThing(@ApiParameter ThingId thingId, @Context ApiCancellation cancellation)
        {
            if (thingId.toString().equals("cancel")) {
                CompletableFuture<String> listenerThread = cancellationListenerThread.get();
                cancellation.onCancel(CANCELLATION_LISTENER_EXECUTOR, () -> listenerThread.complete(Thread.currentThread().getName()));
            }
            return new Thing(new ApiResourceVersion(1), thingId, Thread.currentThread().getName(), cancellation.isCancelled() ? 1 : 0, Optional.empty());
        }
    }

    private static ThreadFactory daemonThreadFactory(String name)
    {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
