package io.airlift.tracing;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.multibindings.ProvidesIntoSet;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import static io.airlift.configuration.ConfigBinder.configBinder;

public class OpenTelemetryExporterModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        configBinder(binder).bindConfig(OpenTelemetryExporterConfig.class);
    }

    @ProvidesIntoSet
    public static SpanProcessor createExporter(OpenTelemetryExporterConfig config)
    {
        SpanExporter exporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(config.getEndpoint())
                .build();
        return BatchSpanProcessor.builder(exporter).build();
    }
}
