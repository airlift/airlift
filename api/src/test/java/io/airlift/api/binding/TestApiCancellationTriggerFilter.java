package io.airlift.api.binding;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static io.airlift.api.binding.ApiCancellationValueParamProvider.REQUEST_CANCELLATION_ATTRIBUTE;

public class TestApiCancellationTriggerFilter
        implements Filter
{
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException
    {
        chain.doFilter(request, response);

        if (request instanceof HttpServletRequest httpRequest && httpRequest.getRequestURI().endsWith("/cancel")) {
            Object cancellation = request.getAttribute(REQUEST_CANCELLATION_ATTRIBUTE);
            if (!(cancellation instanceof CompletableFuture<?> requestCancellation)) {
                throw new ServletException("Expected request cancellation future");
            }
            requestCancellation.cancel(false);
        }
    }
}
