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
                .setInterval(new Duration(1, TimeUnit.MINUTES)));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("otel.exporter.endpoint", "http://example.com:1234")
                .put("otel.exporter.protocol", "http/protobuf")
                .put("otel.exporter.interval", "5m")
                .buildOrThrow();

        OpenTelemetryExporterConfig expected = new OpenTelemetryExporterConfig()
                .setEndpoint("http://example.com:1234")
                .setProtocol(HTTP_PROTOBUF)
                .setInterval(new Duration(5, TimeUnit.MINUTES));

        assertFullMapping(properties, expected);
    }
}
