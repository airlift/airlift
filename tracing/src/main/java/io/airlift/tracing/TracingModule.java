package io.airlift.tracing;

import com.google.inject.Binder;
import com.google.inject.Provides;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.airlift.tracing.SpanSerialization.SpanDeserializer;
import io.airlift.tracing.SpanSerialization.SpanSerializer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

import static io.airlift.json.JsonBinder.jsonBinder;
import static java.util.Objects.requireNonNull;

public class TracingModule
        extends AbstractConfigurationAwareModule
{
    private final String serviceName;
    private final String serviceVersion;

    public TracingModule(String serviceName, String serviceVersion)
    {
        this.serviceName = requireNonNull(serviceName, "serviceName is null");
        this.serviceVersion = requireNonNull(serviceVersion, "serviceVersion is null");
    }

    @Override
    protected void setup(Binder binder)
    {
        if (buildConfigObject(TracingEnabledConfig.class).isEnabled()) {
            install(new OpenTelemetryModule(serviceName, serviceVersion));
        }
        else {
            binder.bind(OpenTelemetry.class).toInstance(OpenTelemetry.noop());
        }

        jsonBinder(binder).addSerializerBinding(Span.class).to(SpanSerializer.class);
        jsonBinder(binder).addDeserializerBinding(Span.class).to(SpanDeserializer.class);
    }

    @Provides
    public Tracer createTracer(OpenTelemetry openTelemetry)
    {
        return openTelemetry.getTracer(serviceName);
    }
}
