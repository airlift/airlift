package io.airlift.opentelemetry;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
                .setProtocol(GRPC));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("otel.exporter.endpoint", "http://example.com:1234")
                .put("otel.exporter.protocol", "http/protobuf")
                .buildOrThrow();

        OpenTelemetryExporterConfig expected = new OpenTelemetryExporterConfig()
                .setEndpoint("http://example.com:1234")
                .setProtocol(HTTP_PROTOBUF);

        assertFullMapping(properties, expected);
    }
}
