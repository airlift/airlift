package io.airlift.http.server;

import com.google.common.collect.ImmutableSet;
import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;

import java.util.Set;

public class HttpTracingConfig
{
    private Set<String> tracingRequestHeaders = ImmutableSet.of();
    private Set<String> tracingResponseHeaders = ImmutableSet.of();

    public Set<String> getTracingRequestHeaders()
    {
        return tracingRequestHeaders;
    }

    @Config("http-server.tracing.request-headers")
    @ConfigDescription("Comma separated list of request header names to include as span attributes (e.g., cf-ray, x-request-id)")
    public HttpTracingConfig setTracingRequestHeaders(Set<String> tracingRequestHeaders)
    {
        this.tracingRequestHeaders = ImmutableSet.copyOf(tracingRequestHeaders);
        return this;
    }

    public Set<String> getTracingResponseHeaders()
    {
        return tracingResponseHeaders;
    }

    @Config("http-server.tracing.response-headers")
    @ConfigDescription("Comma separated list of response header names to include as span attributes (e.g., cf-ray, x-request-id)")
    public HttpTracingConfig setTracingResponseHeaders(Set<String> tracingResponseHeaders)
    {
        this.tracingResponseHeaders = ImmutableSet.copyOf(tracingResponseHeaders);
        return this;
    }
}
