package io.airlift.opentelemetry;

import com.google.common.collect.ImmutableMap;
import io.airlift.units.Duration;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;
import static io.airlift.opentelemetry.OpenTelemetryExporterConfig.Protocol.GRPC;
import static io.airlift.opentelemetry.OpenTelemetryExporterConfig.Protocol.HTTP_PROTOBUF;

public class TestOpenTelemetryExporterConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(OpenTelemetryExporterConfig.class)
                .setEndpoint("http://localhost:4317")
                .setProtocol(GRPC)
                .setInterval(new Duration(1, TimeUnit.MINUTES))
                .setSpanMaxExportBatchSize(null)
                .setSpanMaxQueueSize(null)
                .setSpanScheduleDelay(null)
                .setLogMaxExportBatchSize(null)
                .setLogMaxQueueSize(null)
                .setLogScheduleDelay(null));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("otel.exporter.endpoint", "http://example.com:1234")
                .put("otel.exporter.protocol", "http/protobuf")
                .put("otel.exporter.interval", "5m")
                .put("otel.exporter.span.max-export-batch-size", "128")
                .put("otel.exporter.span.max-queue-size", "4096")
                .put("otel.exporter.span.schedule-delay", "2s")
                .put("otel.exporter.log.max-export-batch-size", "64")
                .put("otel.exporter.log.max-queue-size", "1024")
                .put("otel.exporter.log.schedule-delay", "5s")
                .buildOrThrow();

        OpenTelemetryExporterConfig expected = new OpenTelemetryExporterConfig()
                .setEndpoint("http://example.com:1234")
                .setProtocol(HTTP_PROTOBUF)
                .setInterval(new Duration(5, TimeUnit.MINUTES))
                .setSpanMaxExportBatchSize(128)
                .setSpanMaxQueueSize(4096)
                .setSpanScheduleDelay(new Duration(2, TimeUnit.SECONDS))
                .setLogMaxExportBatchSize(64)
                .setLogMaxQueueSize(1024)
                .setLogScheduleDelay(new Duration(5, TimeUnit.SECONDS));

        assertFullMapping(properties, expected);
    }
}
