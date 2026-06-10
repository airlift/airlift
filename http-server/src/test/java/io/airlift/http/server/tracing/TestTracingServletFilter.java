package io.airlift.http.server.tracing;

import com.google.common.collect.ImmutableSet;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.HttpServer.ClientCertificate;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.ServerFeature;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.node.NodeInfo;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.tracing.Tracing.noopTracer;
import static io.opentelemetry.api.OpenTelemetry.noop;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;

public class TestTracingServletFilter
{
    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
    public static final HeaderName TRACEPARENT_HEADER = HeaderName.of("traceparent");

    @Test
    public void testNoopTelemetrySkipsTracing()
            throws Exception
    {
        SpanCapturingServlet servlet = new SpanCapturingServlet();
        TracingServletFilter filter = new TracingServletFilter(noop(), noopTracer());
        TestingHttpServer server = createServer(servlet, filter);
        try {
            server.start();
            try (HttpClient client = new JettyHttpClient(new HttpClientConfig())) {
                Request request = prepareGet()
                        .setUri(server.getBaseUrl())
                        .build();
                int statusCode = client.execute(request, createStatusResponseHandler()).getStatusCode();
                assertThat(statusCode).isEqualTo(SC_OK);
            }
            assertThat(servlet.span).isNull();
        }
        finally {
            server.stop();
        }
    }

    @Test
    public void testPropagatingTelemetryTraces()
            throws Exception
    {
        SpanCapturingServlet servlet = new SpanCapturingServlet();
        TracingServletFilter filter = new TracingServletFilter(
                OpenTelemetry.propagating(ContextPropagators.create(W3CTraceContextPropagator.getInstance())),
                noopTracer());
        TestingHttpServer server = createServer(servlet, filter);
        try {
            server.start();
            try (HttpClient client = new JettyHttpClient(new HttpClientConfig())) {
                Request request = prepareGet()
                        .setUri(server.getBaseUrl())
                        .addHeader(TRACEPARENT_HEADER, "00-" + TRACE_ID + "-0123456789abcdef-01")
                        .build();
                int statusCode = client.execute(request, createStatusResponseHandler()).getStatusCode();
                assertThat(statusCode).isEqualTo(SC_OK);
            }
            // the noop tracer propagates the extracted parent context, so the span must
            // carry the trace id from the incoming traceparent header
            assertThat(servlet.span).isInstanceOf(Span.class);
            assertThat(((Span) servlet.span).getSpanContext().getTraceId()).isEqualTo(TRACE_ID);
        }
        finally {
            server.stop();
        }
    }

    private static TestingHttpServer createServer(Servlet servlet, TracingServletFilter filter)
            throws IOException
    {
        NodeInfo nodeInfo = new NodeInfo("test");
        HttpServerConfig config = new HttpServerConfig().setHttpPort(0);
        HttpServerInfo httpServerInfo = new HttpServerInfo(config, nodeInfo);
        return new TestingHttpServer(
                "testing",
                httpServerInfo,
                nodeInfo,
                config,
                Optional.empty(),
                servlet,
                ImmutableSet.of(filter),
                ImmutableSet.of(),
                ServerFeature.builder().build(),
                ClientCertificate.NONE);
    }

    private static class SpanCapturingServlet
            extends HttpServlet
    {
        private volatile Object span;

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
        {
            span = request.getAttribute(TracingServletFilter.REQUEST_SPAN);
            response.setStatus(SC_OK);
        }
    }
}
