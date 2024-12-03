package io.airlift.jaxrs.tracing;

import com.google.inject.Inject;
import io.opentelemetry.api.OpenTelemetry;
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
import io.opentelemetry.semconv.ExceptionAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.HttpHeaders;

import java.io.IOException;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static io.airlift.jaxrs.tracing.TracingFilter.REQUEST_SCOPE;
import static io.airlift.jaxrs.tracing.TracingFilter.REQUEST_SPAN;
import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;

public final class TracingServletFilter
        implements Filter
{
    private final TextMapPropagator propagator;
    private final Tracer tracer;

    @Inject
    public TracingServletFilter(OpenTelemetry openTelemetry, Tracer tracer)
    {
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
        this.tracer = requireNonNull(tracer, "tracer is null");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException
    {
        if (!(request instanceof HttpServletRequest httpRequest) || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        Context parent = propagator.extract(Context.root(), httpRequest, ServletTextMapGetter.INSTANCE);
        String method = httpRequest.getMethod().toUpperCase(ENGLISH);

        SpanBuilder spanBuilder = tracer.spanBuilder(method + " " + httpRequest.getRequestURI())
                .setParent(parent)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, method)
                .setAttribute(UrlAttributes.URL_SCHEME, httpRequest.getScheme())
                .setAttribute(ServerAttributes.SERVER_ADDRESS, httpRequest.getServerName())
                .setAttribute(ServerAttributes.SERVER_PORT, getPort(httpRequest))
                .setAttribute(ClientAttributes.CLIENT_ADDRESS, request.getRemoteAddr());

        if (request.getContentLengthLong() > 0) {
            spanBuilder.setAttribute(HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE, request.getContentLengthLong());
        }

        String target = getTarget(httpRequest);
        if (!isNullOrEmpty(target)) {
            spanBuilder.setAttribute(UrlAttributes.URL_PATH, target);
        }

        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);
        if (!isNullOrEmpty(userAgent)) {
            spanBuilder.setAttribute(UserAgentAttributes.USER_AGENT_ORIGINAL, userAgent);
        }

        Span span = spanBuilder.startSpan();
        request.setAttribute(REQUEST_SPAN, span);

        try (Scope scope = span.makeCurrent()) {
            // Add to request attributes for TracingFilter to be able to update Span attributes
            request.setAttribute(REQUEST_SCOPE, scope);
            chain.doFilter(request, response);

            // ignore requests such as GET that might have a content length
            if (httpResponse.containsHeader(HttpHeaders.CONTENT_LENGTH)) {
                String length = nullToEmpty(httpResponse.getHeader(HttpHeaders.CONTENT_LENGTH));
                if (!length.isEmpty()) {
                    span.setAttribute(HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE, Long.parseLong(length));
                }
            }
            if (httpResponse.getStatus() > 0) {
                span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, httpResponse.getStatus());
            }
        }
        catch (Throwable t) {
            span.setStatus(StatusCode.ERROR, t.getMessage());
            span.recordException(t, Attributes.of(ExceptionAttributes.EXCEPTION_ESCAPED, true));
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
}
