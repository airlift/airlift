package io.airlift.tracing;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.multibindings.ProvidesIntoSet;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
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
        SpanExporter exporter = createSpanExporter(config);
        return BatchSpanProcessor.builder(exporter).build();
    }

    static SpanExporter createSpanExporter(OpenTelemetryExporterConfig config)
    {
        return switch (config.getProtocol()) {
            case "grpc" -> OtlpGrpcSpanExporter.builder()
                    .setEndpoint(config.getEndpoint())
                    .build();
            case "http/protobuf" -> OtlpHttpSpanExporter.builder()
                    .setEndpoint(config.getEndpoint())
                    .build();
            default -> throw new IllegalArgumentException("Unsupported protocol: " + config.getProtocol());
        };
    }
}
