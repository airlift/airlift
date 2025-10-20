package io.airlift.opentelemetry;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;

import static io.airlift.opentelemetry.OpenTelemetryExporterConfig.Protocol.GRPC;
import static io.airlift.opentelemetry.OpenTelemetryExporterConfig.Protocol.HTTP_PROTOBUF;
import static org.assertj.core.api.Assertions.assertThat;

final class TestOpenTelemetryExporterModule
{
    @Test
    void testGrpcExporterIsCreated()
    {
        OpenTelemetryExporterConfig config = new OpenTelemetryExporterConfig()
                .setProtocol(GRPC)
                .setEndpoint("http://localhost:4317");

        SpanExporter exporter = OpenTelemetryExporterModule.createSpanExporter(config);
        assertThat(exporter).isInstanceOf(OtlpGrpcSpanExporter.class);
    }

    @Test
    void testHttpExporterIsCreated()
    {
        OpenTelemetryExporterConfig config = new OpenTelemetryExporterConfig()
                .setProtocol(HTTP_PROTOBUF)
                .setEndpoint("http://localhost:4317");

        SpanExporter exporter = OpenTelemetryExporterModule.createSpanExporter(config);
        assertThat(exporter).isInstanceOf(OtlpHttpSpanExporter.class);
    }
}
