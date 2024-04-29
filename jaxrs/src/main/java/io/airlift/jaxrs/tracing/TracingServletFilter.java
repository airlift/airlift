package io.airlift.jaxrs.tracing;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.ExceptionAttributes;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;

public final class TracingServletFilter
        implements Filter
{
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException
    {
        request.setAttribute(TracingFilter.REMOTE_ADDRESS, request.getRemoteAddr());

        try {
            chain.doFilter(request, response);
        }
        catch (Throwable t) {
            if (request.getAttribute(TracingFilter.REQUEST_SPAN) instanceof Span span) {
                span.setStatus(StatusCode.ERROR, t.getMessage());
                span.recordException(t, Attributes.of(ExceptionAttributes.EXCEPTION_ESCAPED, true));
            }
            throw t;
        }
        finally {
            if (request.getAttribute(TracingFilter.REQUEST_SCOPE) instanceof Scope scope) {
                scope.close();
            }
        }
    }
}
