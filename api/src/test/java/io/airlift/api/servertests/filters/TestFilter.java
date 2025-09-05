package io.airlift.api.servertests.filters;

import com.google.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class TestFilter
        implements ContainerRequestFilter, ContainerResponseFilter
{
    private final Map<String, AtomicInteger> counters;

    @Inject
    public TestFilter(Map<String, AtomicInteger> counters)
    {
        this.counters = counters;
    }

    @Override
    public void filter(ContainerRequestContext requestContext)
    {
        counters.computeIfAbsent("request", ignore -> new AtomicInteger()).incrementAndGet();
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
    {
        counters.computeIfAbsent("response", ignore -> new AtomicInteger()).incrementAndGet();
    }
}
