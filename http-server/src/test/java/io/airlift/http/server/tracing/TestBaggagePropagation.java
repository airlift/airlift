package io.airlift.http.server.tracing;

import com.google.common.collect.ImmutableMap;
import com.google.inject.BindingAnnotation;
import com.google.inject.Injector;
import com.google.inject.Key;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.http.server.HttpServer;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.HttpServerModule;
import io.airlift.node.testing.TestingNodeModule;
import io.airlift.opentelemetry.OpenTelemetryModule;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.URI;
import java.nio.file.Path;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.http.client.HttpClientBinder.httpClientBinder;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test that boots two independent services and verifies that
 * OpenTelemetry baggage set on the calling (client) service is propagated over HTTP and is
 * visible in the active context of the receiving (server) service.
 * The client and server each only allowlist the {@code orderId} baggage key.
 */
public class TestBaggagePropagation
{
    @Test
    public void testBaggagePropagatesBetweenBootstrappedServices(@TempDir Path tempDir)
            throws Exception
    {
        @SuppressWarnings("resource")
        InMemorySpanExporter serverSpanExporter = InMemorySpanExporter.create();

        // Service A: an HTTP server that echoes back the "orderId" baggage entry it observes.
        Injector serverInjector = new Bootstrap(
                new TestingNodeModule(),
                new HttpServerModule(),
                new OpenTelemetryModule("server-service", "1.0"),
                binder -> newSetBinder(binder, SpanProcessor.class).addBinding()
                        .toInstance(SimpleSpanProcessor.create(serverSpanExporter)),
                binder -> binder.bind(Servlet.class).to(BaggageEchoServlet.class))
                .setRequiredConfigurationProperties(ImmutableMap.<String, String>builder()
                        .put("http-server.http.port", "0")
                        .put("http-server.log.path", tempDir.resolve("http-request.log").toString())
                        .put("otel.tracing.baggage.allowed-keys", "orderId")
                        .buildOrThrow())
                .doNotInitializeLogging()
                .quiet()
                .initialize();

        HttpServer server = serverInjector.getInstance(HttpServer.class);
        server.start();
        try {
            URI serverUri = serverInjector.getInstance(HttpServerInfo.class).getHttpUri();

            // Service B: an HTTP client wired through Bootstrap with the same telemetry stack, so the
            // injected client uses the propagators provided by OpenTelemetryModule.
            Injector clientInjector = new Bootstrap(
                    new TestingNodeModule(),
                    new OpenTelemetryModule("client-service", "1.0"),
                    binder -> newSetBinder(binder, SpanProcessor.class).addBinding()
                            .toInstance(SpanProcessor.composite()),
                    binder -> httpClientBinder(binder).bindHttpClient("test", TestClient.class))
                    .setRequiredConfigurationProperties(ImmutableMap.of("otel.tracing.baggage.allowed-keys", "orderId"))
                    .doNotInitializeLogging()
                    .quiet()
                    .initialize();

            HttpClient client = clientInjector.getInstance(Key.get(HttpClient.class, TestClient.class));

            // Set request-scoped baggage on the client side, including a key ("secret") that neither
            // side allowlists; the client injects the allowlisted baggage as the "baggage" header when
            // the context is current at execution time.
            Baggage baggage = Baggage.builder()
                    .put("orderId", "42")
                    .put("secret", "leaked")
                    .build();
            try (Scope ignored = baggage.makeCurrent()) {
                StringResponse response = client.execute(
                        prepareGet().setUri(serverUri).build(),
                        createStringResponseHandler());

                assertThat(response.getStatusCode()).isEqualTo(SC_OK);
                assertThat(response.getBody()).isEqualTo("42");
                assertThat(response.getHeader(HeaderName.of("X-Secret"))).contains("<none>");
            }

            // Baggage is also surfaced as span attributes, not only in logs, and only for allowlisted keys.
            assertThat(serverSpanExporter.getFinishedSpanItems()).hasSize(1);
            SpanData serverSpan = serverSpanExporter.getFinishedSpanItems().stream().collect(onlyElement());
            assertThat(serverSpan.getAttributes().get(AttributeKey.stringKey("baggage.orderId"))).isEqualTo("42");
            assertThat(serverSpan.getAttributes().get(AttributeKey.stringKey("baggage.secret"))).isNull();
        }
        finally {
            server.stop();
        }
    }

    private static class BaggageEchoServlet
            extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException
        {
            Baggage baggage = Baggage.fromContext(Context.current());
            String orderId = baggage.getEntryValue("orderId");
            String secret = baggage.getEntryValue("secret");
            response.setHeader("X-Secret", secret == null ? "<none>" : secret);
            response.setStatus(SC_OK);
            response.getWriter().write(orderId == null ? "<none>" : orderId);
        }
    }

    @Retention(RUNTIME)
    @Target({FIELD, PARAMETER, METHOD})
    @BindingAnnotation
    public @interface TestClient {}
}
