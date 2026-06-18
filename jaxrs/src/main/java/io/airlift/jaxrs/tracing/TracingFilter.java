package io.airlift.jaxrs.tracing;

import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.semconv.CodeAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.uri.UriTemplate;

import java.util.List;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.requireNonNull;

@Priority(0)
public final class TracingFilter
        implements ContainerRequestFilter
{
    // Same as TracingServletFilter.REQUEST_SPAN
    static final String REQUEST_SPAN = "airlift.trace-span";

    private final String codeFunctionName;

    public TracingFilter(String className, String methodName)
    {
        requireNonNull(className, "className is null");
        requireNonNull(methodName, "methodName is null");
        this.codeFunctionName = className + "." + methodName;
    }

    @Override
    public void filter(ContainerRequestContext requestContext)
    {
        ContainerRequest request = (ContainerRequest) requestContext.getRequest();
        if (request == null) {
            // Request was already recycled
            return;
        }

        // Update the span with information obtained from JAX-RS.
        if (requestContext.getProperty(REQUEST_SPAN) instanceof Span span && span.isRecording()) {
            String route = route(request.getUriInfo().getMatchedTemplates());
            span.updateName(requestContext.getMethod() + " " + route);
            span.setAttribute(HttpAttributes.HTTP_ROUTE, route);
            span.setAttribute(CodeAttributes.CODE_FUNCTION_NAME, codeFunctionName);
        }
    }

    @VisibleForTesting
    static String route(List<UriTemplate> matchedTemplates)
    {
        // matched templates are ordered from most to least recently matched, so the route is built in reverse
        StringBuilder route = new StringBuilder();
        for (int i = matchedTemplates.size() - 1; i >= 0; i--) {
            appendNormalized(route, matchedTemplates.get(i).getTemplate());
        }
        return route.toString();
    }

    private static void appendNormalized(StringBuilder route, String path)
    {
        if (isNullOrEmpty(path) || path.equals("/")) {
            return;
        }

        if (!path.startsWith("/")) {
            route.append('/');
        }

        int length = path.length();
        if (path.endsWith("/")) {
            length--;
        }
        route.append(path, 0, length);
    }
}
