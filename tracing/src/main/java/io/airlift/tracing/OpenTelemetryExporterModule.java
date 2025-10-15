package io.airlift.tracing;

import static io.airlift.configuration.ConfigBinder.configBinder;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.multibindings.ProvidesIntoSet;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class OpenTelemetryExporterModule implements Module {
    @Override
    public void configure(Binder binder) {
        configBinder(binder).bindConfig(OpenTelemetryExporterConfig.class);
    }

    @ProvidesIntoSet
    public static SpanProcessor createExporter(OpenTelemetryExporterConfig config) {
        return BatchSpanProcessor.builder(createSpanExporter(config)).build();
    }

    static SpanExporter createSpanExporter(OpenTelemetryExporterConfig config) {
        return switch (config.getProtocol()) {
            case GRPC ->
                OtlpGrpcSpanExporter.builder().setEndpoint(config.getEndpoint()).build();
            case HTTP_PROTOBUF ->
                OtlpHttpSpanExporter.builder().setEndpoint(config.getEndpoint()).build();
        };
    }
}
