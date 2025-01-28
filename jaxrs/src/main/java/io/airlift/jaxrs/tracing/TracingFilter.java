package io.airlift.jaxrs.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import org.glassfish.jersey.server.ContainerRequest;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.requireNonNull;

@Priority(0)
public final class TracingFilter
        implements ContainerRequestFilter
{
    // Same as TracingServletFilter.REQUEST_SPAN
    static final String REQUEST_SPAN = "airlift.trace-span";

    private final String className;
    private final String methodName;

    public TracingFilter(String className, String methodName)
    {
        this.className = requireNonNull(className, "className is null");
        this.methodName = requireNonNull(methodName, "methodName is null");
    }

    @Override
    public void filter(ContainerRequestContext requestContext)
    {
        ContainerRequest request = (ContainerRequest) requestContext.getRequest();
        if (request == null) {
            // Request was already recycled
            return;
        }

        String route = request.getUriInfo().getMatchedTemplates().stream()
                .map(template -> normalizePath(template.getTemplate()))
                .reduce((first, second) -> second + first)
                .orElseThrow();

        // Update the span with information obtained from JAX-RS
        if (requestContext.getProperty(REQUEST_SPAN) instanceof Span span) {
            span.updateName(requestContext.getMethod() + " " + route);
            span.setAttribute(HttpAttributes.HTTP_ROUTE, route);
            span.setAttribute(CodeIncubatingAttributes.CODE_NAMESPACE, className);
            span.setAttribute(CodeIncubatingAttributes.CODE_FUNCTION_NAME, methodName);
        }
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
}
