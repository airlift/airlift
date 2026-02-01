package io.airlift.http.server.tracing;

import com.google.inject.Inject;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.ClientAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;
import io.opentelemetry.semconv.incubating.TlsIncubatingAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;
import java.util.function.Consumer;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.net.HttpHeaders.CONTENT_LENGTH;
import static com.google.common.net.HttpHeaders.USER_AGENT;
import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;
import static org.eclipse.jetty.ee11.servlet.ServletContextRequest.SSL_CIPHER_SUITE;
import static org.eclipse.jetty.ee11.servlet.ServletContextRequest.SSL_SESSION_ID;

public final class TracingServletFilter
        extends HttpFilter
{
    // This attribute will be deprecated in OTEL soon
    static final AttributeKey<Boolean> EXCEPTION_ESCAPED = AttributeKey.booleanKey("exception.escaped");
    static final String REQUEST_SPAN = "airlift.trace-span";

    private final TextMapPropagator propagator;
    private final Tracer tracer;

    @Inject
    public TracingServletFilter(OpenTelemetry openTelemetry, Tracer tracer)
    {
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
        this.tracer = requireNonNull(tracer, "tracer is null");
    }

    public static void updateRequestSpan(HttpServletRequest request, Consumer<Span> spanConsumer)
    {
        if (request.getAttribute(REQUEST_SPAN) instanceof Span span) {
            spanConsumer.accept(span);
        }
    }

    @Override
    public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException
    {
        Context parent = propagator.extract(Context.root(), request, ServletTextMapGetter.INSTANCE);
        String method = request.getMethod().toUpperCase(ENGLISH);

        SpanBuilder spanBuilder = tracer.spanBuilder(method + " " + request.getRequestURI())
                .setParent(parent)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, method)
                .setAttribute(UrlAttributes.URL_SCHEME, request.getScheme())
                .setAttribute(ServerAttributes.SERVER_ADDRESS, request.getServerName())
                .setAttribute(ServerAttributes.SERVER_PORT, getPort(request))
                .setAttribute(ClientAttributes.CLIENT_ADDRESS, request.getRemoteAddr())
                .setAttribute(NetworkAttributes.NETWORK_PROTOCOL_NAME, "http");

        String sessionId = (String) request.getAttribute(SSL_SESSION_ID);
        if (sessionId != null) {
            spanBuilder.setAttribute(TlsIncubatingAttributes.TLS_ESTABLISHED, true);
            spanBuilder.setAttribute(TlsIncubatingAttributes.TLS_CIPHER, (String) request.getAttribute(SSL_CIPHER_SUITE));
        }

        if (request.getProtocol().equalsIgnoreCase("HTTP/1.1")) {
            spanBuilder.setAttribute(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1");
        }
        else if (request.getProtocol().equalsIgnoreCase("HTTP/2.0")) {
            spanBuilder.setAttribute(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "2.0");
        }

        if (request.getContentLengthLong() > 0) {
            spanBuilder.setAttribute(HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE, request.getContentLengthLong());
        }

        String target = getTarget(request);
        if (!isNullOrEmpty(target)) {
            spanBuilder.setAttribute(UrlAttributes.URL_PATH, target);
        }

        String userAgent = request.getHeader(USER_AGENT);
        if (!isNullOrEmpty(userAgent)) {
            spanBuilder.setAttribute(UserAgentAttributes.USER_AGENT_ORIGINAL, userAgent);
        }

        Span span = spanBuilder.startSpan();
        // Add to request attributes for TracingFilter to be able to update Span attributes
        request.setAttribute(REQUEST_SPAN, span);

        try (Scope ignored = span.makeCurrent()) {
            chain.doFilter(request, new TracingHttpServletResponse(response, span));
        }
        catch (Throwable t) {
            span.setStatus(StatusCode.ERROR, t.getMessage());
            span.recordException(t, Attributes.of(EXCEPTION_ESCAPED, true));
            throw t;
        }
        finally {
            span.end();
        }
    }

    private static class ServletTextMapGetter
            implements TextMapGetter<HttpServletRequest>
    {
        public static final ServletTextMapGetter INSTANCE = new ServletTextMapGetter();

        @Override
        public Iterable<String> keys(HttpServletRequest request)
        {
            return request.getHeaderNames()::asIterator;
        }

        @Override
        public String get(HttpServletRequest request, String key)
        {
            return requireNonNull(request).getHeader(key);
        }
    }

    private static String getTarget(HttpServletRequest request)
    {
        String target = nullToEmpty(request.getRequestURI());
        if (request.getQueryString() != null) {
            target += "?" + request.getQueryString();
        }
        return target;
    }

    private static long getPort(HttpServletRequest request)
    {
        int port = request.getServerPort();
        if (port > 0) {
            return port;
        }
        return switch (nullToEmpty(request.getScheme()).toLowerCase(ENGLISH)) {
            case "http" -> 80;
            case "https" -> 443;
            default -> -1;
        };
    }

    private static class TracingHttpServletResponse
            extends HttpServletResponseWrapper
    {
        private final Span span;

        public TracingHttpServletResponse(HttpServletResponse delegate, Span span)
        {
            super(delegate);
            this.span = requireNonNull(span, "span is null");
        }

        @Override
        public void sendError(int statusCode, String msg)
                throws IOException
        {
            span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, statusCode);
            span.setStatus(StatusCode.ERROR, msg);
            super.sendError(statusCode, msg);
        }

        @Override
        public void sendError(int statusCode)
                throws IOException
        {
            span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, statusCode);
            span.setStatus(StatusCode.ERROR);
            super.sendError(statusCode);
        }

        @Override
        public void setStatus(int statusCode)
        {
            span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, statusCode);
            super.setStatus(statusCode);
        }

        @Override
        public void setHeader(String name, String value)
        {
            if (name.equalsIgnoreCase(CONTENT_LENGTH)) {
                span.setAttribute(HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE, Long.parseLong(value));
            }
            super.setHeader(name, value);
        }

        @Override
        public void addHeader(String name, String value)
        {
            if (name.equalsIgnoreCase(CONTENT_LENGTH)) {
                span.setAttribute(HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE, Long.parseLong(value));
            }
            super.addHeader(name, value);
        }

        @Override
        public void setIntHeader(String name, int value)
        {
            if (name.equalsIgnoreCase(CONTENT_LENGTH)) {
                span.setAttribute(HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE, value);
            }
            super.setIntHeader(name, value);
        }

        @Override
        public void addIntHeader(String name, int value)
        {
            if (name.equalsIgnoreCase(CONTENT_LENGTH)) {
                span.setAttribute(HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE, value);
            }
            super.addIntHeader(name, value);
        }

        @Override
        public void setContentLength(int length)
        {
            span.setAttribute(HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE, length);
            super.setContentLength(length);
        }

        @Override
        public void setContentLengthLong(long length)
        {
            span.setAttribute(HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE, length);
            super.setContentLengthLong(length);
        }
    }
}
