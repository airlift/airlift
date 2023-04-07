package io.airlift.jaxrs.tracing;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

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
                span.recordException(t, Attributes.of(SemanticAttributes.EXCEPTION_ESCAPED, true));
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
