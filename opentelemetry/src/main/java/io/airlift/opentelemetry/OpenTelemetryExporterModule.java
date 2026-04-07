package io.airlift.opentelemetry;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.multibindings.ProvidesIntoSet;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessorBuilder;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessorBuilder;
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
    public static SpanProcessor createSpanProcessor(OpenTelemetryExporterConfig config, SdkMeterProvider meterProvider)
    {
        BatchSpanProcessorBuilder builder = BatchSpanProcessor.builder(createSpanExporter(config))
                .setMeterProvider(meterProvider);
        config.getSpanMaxExportBatchSize().ifPresent(builder::setMaxExportBatchSize);
        config.getSpanMaxQueueSize().ifPresent(builder::setMaxQueueSize);
        config.getSpanScheduleDelay().ifPresent(delay -> builder.setScheduleDelay(delay.toJavaTime()));
        return builder.build();
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

    @ProvidesIntoSet
    public static LogRecordProcessor createLogRecordProcessor(OpenTelemetryExporterConfig config, SdkMeterProvider meterProvider)
    {
        BatchLogRecordProcessorBuilder builder = BatchLogRecordProcessor.builder(createLogRecordExporter(config))
                .setMeterProvider(meterProvider);
        config.getLogMaxExportBatchSize().ifPresent(builder::setMaxExportBatchSize);
        config.getLogMaxQueueSize().ifPresent(builder::setMaxQueueSize);
        config.getLogScheduleDelay().ifPresent(delay -> builder.setScheduleDelay(delay.toJavaTime()));
        return builder.build();
    }

    static LogRecordExporter createLogRecordExporter(OpenTelemetryExporterConfig config)
    {
        return switch (config.getProtocol()) {
            case GRPC -> OtlpGrpcLogRecordExporter.builder()
                    .setEndpoint(config.getEndpoint())
                    .build();
            case HTTP_PROTOBUF -> OtlpHttpLogRecordExporter.builder()
                    .setEndpoint(config.getEndpoint())
                    .build();
        };
    }
}
