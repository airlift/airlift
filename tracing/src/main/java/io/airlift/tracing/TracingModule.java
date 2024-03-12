package io.airlift.tracing;

import com.google.inject.Binder;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.airlift.tracing.SpanSerialization.SpanDeserializer;
import io.airlift.tracing.SpanSerialization.SpanSerializer;
import io.opentelemetry.api.trace.Span;

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
        install(new OpenTelemetryModule(serviceName, serviceVersion));

        if (buildConfigObject(TracingEnabledConfig.class).isEnabled()) {
            binder.install(new OpenTelemetryExporterModule());
        }

        jsonBinder(binder).addSerializerBinding(Span.class).to(SpanSerializer.class);
        jsonBinder(binder).addDeserializerBinding(Span.class).to(SpanDeserializer.class);
    }
}
