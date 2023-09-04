package io.airlift.tracing;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import io.airlift.tracing.SpanSerialization.SpanDeserializer;
import io.airlift.tracing.SpanSerialization.SpanSerializer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

import static io.airlift.json.JsonBinder.jsonBinder;
import static java.util.Objects.requireNonNull;

public class TracingSupportModule
        implements Module
{
    private final String serviceName;

    public TracingSupportModule(String serviceName)
    {
        this.serviceName = requireNonNull(serviceName, "serviceName is null");
    }

    @Override
    public void configure(Binder binder)
    {
        jsonBinder(binder).addSerializerBinding(Span.class).to(SpanSerializer.class);
        jsonBinder(binder).addDeserializerBinding(Span.class).to(SpanDeserializer.class);
    }

    @Provides
    public Tracer createTracer(OpenTelemetry openTelemetry)
    {
        return openTelemetry.getTracer(serviceName);
    }
}
