package io.airlift.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;

import static java.util.Objects.requireNonNull;

final class FilteringOpenTelemetry
        implements OpenTelemetry
{
    private final OpenTelemetry delegate;
    private final TracerProvider tracerProvider;

    FilteringOpenTelemetry(OpenTelemetry delegate, TracerProvider tracerProvider)
    {
        this.delegate = requireNonNull(delegate, "delegate is null");
        this.tracerProvider = requireNonNull(tracerProvider, "tracerProvider is null");
    }

    @Override
    public TracerProvider getTracerProvider()
    {
        return tracerProvider;
    }

    @Override
    public MeterProvider getMeterProvider()
    {
        return delegate.getMeterProvider();
    }

    @Override
    public LoggerProvider getLogsBridge()
    {
        return delegate.getLogsBridge();
    }

    @Override
    public ContextPropagators getPropagators()
    {
        return delegate.getPropagators();
    }
}
