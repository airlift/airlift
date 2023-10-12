package io.airlift.jaxrs.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.SemanticAttributes;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import org.glassfish.jersey.server.ContainerRequest;

import java.net.URI;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;

@Priority(0)
public final class TracingFilter
        implements ContainerRequestFilter, ContainerResponseFilter
{
    static final String REMOTE_ADDRESS = "airlift.remote-address";
    static final String REQUEST_SCOPE = "airlift.trace-scope";
    static final String REQUEST_SPAN = "airlift.trace-span";

    private final TextMapPropagator propagator;
    private final Tracer tracer;
    private final String className;
    private final String methodName;

    public TracingFilter(OpenTelemetry openTelemetry, Tracer tracer, String className, String methodName)
    {
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
        this.tracer = requireNonNull(tracer, "tracer is null");
        this.className = requireNonNull(className, "className is null");
        this.methodName = requireNonNull(methodName, "methodName is null");
    }

    @Override
    public void filter(ContainerRequestContext requestContext)
    {
        Context parent = propagator.extract(Context.root(), requestContext, JaxrsTextMapGetter.INSTANCE);

        ContainerRequest request = (ContainerRequest) requestContext.getRequest();

        String method = request.getMethod().toUpperCase(ENGLISH);
        URI uri = request.getRequestUri();

        String route = request.getUriInfo().getMatchedTemplates().stream()
                .map(template -> normalizePath(template.getTemplate()))
                .reduce((first, second) -> second + first)
                .orElseThrow();

        SpanBuilder spanBuilder = tracer.spanBuilder(method + " " + route)
                .setParent(parent)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(SemanticAttributes.HTTP_REQUEST_METHOD, method)
                .setAttribute(SemanticAttributes.HTTP_ROUTE, route)
                .setAttribute(SemanticAttributes.URL_SCHEME, uri.getScheme())
                .setAttribute(SemanticAttributes.SERVER_ADDRESS, uri.getHost())
                .setAttribute(SemanticAttributes.CODE_NAMESPACE, className)
                .setAttribute(SemanticAttributes.CODE_FUNCTION, methodName);

        String target = getTarget(uri);
        if (!isNullOrEmpty(target)) {
            spanBuilder.setAttribute(SemanticAttributes.URL_PATH, target);
        }

        int port = getPort(uri);
        if (port > 0) {
            spanBuilder.setAttribute(SemanticAttributes.SERVER_PORT, (long) port);
        }

        String remoteAddress = (String) request.getProperty(REMOTE_ADDRESS);
        if (!isNullOrEmpty(remoteAddress)) {
            spanBuilder.setAttribute(SemanticAttributes.CLIENT_ADDRESS, remoteAddress);
        }

        String userAgent = request.getHeaderString(HttpHeaders.USER_AGENT);
        if (!isNullOrEmpty(userAgent)) {
            spanBuilder.setAttribute(SemanticAttributes.USER_AGENT_ORIGINAL, userAgent);
        }

        // ignore requests such as GET that might have a content length
        if (request.hasEntity() && request.getLength() >= 0) {
            spanBuilder.setAttribute(SemanticAttributes.HTTP_REQUEST_BODY_SIZE, (long) request.getLength());
        }

        Span span = spanBuilder.startSpan();
        requestContext.setProperty(REQUEST_SPAN, span);
        requestContext.setProperty(REQUEST_SCOPE, span.makeCurrent());
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response)
    {
        Scope scope = (Scope) request.getProperty(REQUEST_SCOPE);
        Span span = (Span) request.getProperty(REQUEST_SPAN);

        try (scope) {
            if (response.getStatus() != -1) {
                span.setAttribute(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, response.getStatus());
            }
            if (response.hasEntity() && (response.getLength() != -1)) {
                span.setAttribute(SemanticAttributes.HTTP_RESPONSE_BODY_SIZE, response.getLength());
            }
        }
        span.end();
    }

    private static String normalizePath(String path)
    {
        if (isNullOrEmpty(path) || path.equals("/")) {
            return "";
        }

        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }

    private static String getTarget(URI uri)
    {
        String target = nullToEmpty(uri.getRawPath());
        if (uri.getRawQuery() != null) {
            target += "?" + uri.getRawQuery();
        }
        return target;
    }

    private static int getPort(URI uri)
    {
        int port = uri.getPort();
        if (port > 0) {
            return port;
        }
        return switch (nullToEmpty(uri.getScheme()).toLowerCase(ENGLISH)) {
            case "http" -> 80;
            case "https" -> 443;
            default -> -1;
        };
    }

    private static class JaxrsTextMapGetter
            implements TextMapGetter<ContainerRequestContext>
    {
        public static final JaxrsTextMapGetter INSTANCE = new JaxrsTextMapGetter();

        @Override
        public Iterable<String> keys(ContainerRequestContext request)
        {
            return request.getHeaders().keySet();
        }

        @Override
        public String get(ContainerRequestContext request, String key)
        {
            return requireNonNull(request).getHeaders().getFirst(key);
        }
    }
}
