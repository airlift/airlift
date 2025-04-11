package io.airlift.tracing;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestOpenTelemetryExporterModule {
    @Test
    public void testGrpcExporterIsCreated() {
        OpenTelemetryExporterConfig config = new OpenTelemetryExporterConfig()
                .setProtocol("grpc")
                .setEndpoint("http://localhost:4317");

        SpanExporter exporter = OpenTelemetryExporterModule.createSpanExporter(config);
        assertInstanceOf(OtlpGrpcSpanExporter.class, exporter);
    }

    @Test
    public void testHttpExporterIsCreated() {
        OpenTelemetryExporterConfig config = new OpenTelemetryExporterConfig()
                .setProtocol("http/protobuf")
                .setEndpoint("http://localhost:4317");

        SpanExporter exporter = OpenTelemetryExporterModule.createSpanExporter(config);
        assertInstanceOf(OtlpHttpSpanExporter.class, exporter);
    }

    @Test
    public void testUnsupportedProtocolThrows() {
        var config = new OpenTelemetryExporterConfig()
                .setProtocol("unsupported")
                .setEndpoint("http://localhost");

        assertThrows(IllegalArgumentException.class, () -> OpenTelemetryExporterModule.createSpanExporter(config));
    }
}
