package io.airlift.jaxrs.tracing;

import com.google.inject.Inject;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;

import static java.util.Objects.requireNonNull;

public class TracingDynamicFeature
        implements DynamicFeature
{
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    @Inject
    public TracingDynamicFeature(OpenTelemetry openTelemetry, Tracer tracer)
    {
        this.openTelemetry = requireNonNull(openTelemetry, "openTelemetry is null");
        this.tracer = requireNonNull(tracer, "tracer is null");
    }

    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext context)
    {
        context.register(new TracingFilter(
                openTelemetry,
                tracer,
                resourceInfo.getResourceClass().getName(),
                resourceInfo.getResourceMethod().getName()));
    }
}
