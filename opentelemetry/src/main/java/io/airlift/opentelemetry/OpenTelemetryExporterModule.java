package io.airlift.opentelemetry;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.multibindings.ProvidesIntoSet;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
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
    public static SpanProcessor createSpanProcessor(OpenTelemetryExporterConfig config)
    {
        return BatchSpanProcessor.builder(createSpanExporter(config)).build();
    }

    static SpanExporter createSpanExporter(OpenTelemetryExporterConfig config)
    {
        return switch (config.getProtocol()) {
            case GRPC -> OtlpGrpcSpanExporter.builder()
                    .setEndpoint(config.getEndpoint())
                    .build();
            case HTTP_PROTOBUF -> OtlpHttpSpanExporter.builder()
                    .setEndpoint(config.getEndpoint())
                    .build();
        };
    }

    @ProvidesIntoSet
    public static MetricReader createMetricReader(OpenTelemetryExporterConfig config)
    {
        return PeriodicMetricReader.builder(createMetricExporter(config))
                .setInterval(config.getInterval().toJavaTime())
                .build();
    }

    static MetricExporter createMetricExporter(OpenTelemetryExporterConfig config)
    {
        return switch (config.getProtocol()) {
            case GRPC -> OtlpGrpcMetricExporter.builder()
                    .setEndpoint(config.getEndpoint())
                    .build();
            case HTTP_PROTOBUF -> OtlpHttpMetricExporter.builder()
                    .setEndpoint(config.getEndpoint())
                    .build();
        };
    }
}
