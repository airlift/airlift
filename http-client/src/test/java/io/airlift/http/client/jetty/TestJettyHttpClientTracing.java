package io.airlift.http.client.jetty;

import com.google.common.collect.ImmutableList;
import io.airlift.http.client.EchoServlet;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.TestingHttpServer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.opentelemetry.api.OpenTelemetry.propagating;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestJettyHttpClientTracing
{
    private static final HeaderName TRACE_HEADER = HeaderName.of("x-test-trace");

    @Test
    public void testPropagatorHeadersInjected()
            throws Exception
    {
        EchoServlet servlet = new EchoServlet();
        try (TestingHttpServer server = new TestingHttpServer(Optional.empty(), servlet);
                JettyHttpClient client = new JettyHttpClient(
                        "tracing-test",
                        new HttpClientConfig(),
                        ImmutableList.of(),
                        propagating(ContextPropagators.create(new TestingPropagator())),
                        TracerProvider.noop().get("testing"),
                        Optional.empty(),
                        Optional.empty())) {
            Request request = prepareGet()
                    .setUri(server.baseURI())
                    .build();
            client.execute(request, createStringResponseHandler());

            assertThat(servlet.getRequestHeaders(TRACE_HEADER)).containsExactly("injected");
        }
    }

    @Test
    public void testNoopTelemetryInjectsNothing()
            throws Exception
    {
        EchoServlet servlet = new EchoServlet();
        try (TestingHttpServer server = new TestingHttpServer(Optional.empty(), servlet);
                JettyHttpClient client = new JettyHttpClient("tracing-test", new HttpClientConfig())) {
            Request request = prepareGet()
                    .setUri(server.baseURI())
                    .build();
            client.execute(request, createStringResponseHandler());

            assertThat(servlet.getRequestHeaders(TRACE_HEADER)).isEmpty();
        }
    }

    @Test
    public void testSpanEndedWhenResponseHandlerThrows()
            throws Exception
    {
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        try (SdkTracerProvider tracerProvider = createTracerProvider(spanExporter)) {
            try (TestingHttpServer server = new TestingHttpServer(Optional.empty(), new EchoServlet());
                    JettyHttpClient client = createClient(tracerProvider)) {
                Request request = prepareGet()
                        .setUri(server.baseURI())
                        .build();

                assertThatThrownBy(() -> client.executeAsync(request, new ThrowingResponseHandler()).get())
                        .cause()
                        .hasMessage("handler failed");
            }

            assertThat(spanExporter.getFinishedSpanItems())
                    .singleElement()
                    .extracting(span -> span.getStatus().getStatusCode())
                    .isEqualTo(StatusCode.ERROR);
        }
    }

    @Test
    public void testSpanEndedWhenExceptionHandlerReturnsValue()
            throws Exception
    {
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        try (SdkTracerProvider tracerProvider = createTracerProvider(spanExporter)) {
            try (JettyHttpClient client = createClient(tracerProvider)) {
                Request request = prepareGet()
                        .setUri(URI.create("http://airlift.invalid/"))
                        .build();

                assertThat(client.executeAsync(request, new RecoveringResponseHandler()).get()).isEqualTo("recovered");
            }

            assertThat(spanExporter.getFinishedSpanItems())
                    .singleElement()
                    .extracting(span -> span.getStatus().getStatusCode())
                    .isEqualTo(StatusCode.ERROR);
        }
    }

    private static SdkTracerProvider createTracerProvider(InMemorySpanExporter spanExporter)
    {
        return SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
    }

    private static JettyHttpClient createClient(SdkTracerProvider tracerProvider)
    {
        return new JettyHttpClient(
                "tracing-test",
                new HttpClientConfig(),
                ImmutableList.of(),
                OpenTelemetry.noop(),
                tracerProvider.get("testing"),
                Optional.empty(),
                Optional.empty());
    }

    private static final class ThrowingResponseHandler
            implements ResponseHandler<String, RuntimeException>
    {
        @Override
        public String handleException(Request request, Exception exception)
        {
            throw new RuntimeException("handler failed", exception);
        }

        @Override
        public String handle(Request request, Response response)
        {
            throw new RuntimeException("handler failed");
        }
    }

    private static final class RecoveringResponseHandler
            implements ResponseHandler<String, RuntimeException>
    {
        @Override
        public String handleException(Request request, Exception exception)
        {
            return "recovered";
        }

        @Override
        public String handle(Request request, Response response)
        {
            return "handled";
        }
    }

    private static final class TestingPropagator
            implements TextMapPropagator
    {
        @Override
        public Collection<String> fields()
        {
            return List.of(TRACE_HEADER.toString());
        }

        @Override
        public <C> void inject(Context context, C carrier, TextMapSetter<C> setter)
        {
            setter.set(carrier, TRACE_HEADER.toString(), "injected");
        }

        @Override
        public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter)
        {
            return context;
        }
    }
}
