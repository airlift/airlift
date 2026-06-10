package io.airlift.http.client.jetty;

import com.google.common.collect.ImmutableList;
import io.airlift.http.client.EchoServlet;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.TestingHttpServer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.opentelemetry.api.OpenTelemetry.propagating;
import static org.assertj.core.api.Assertions.assertThat;

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
