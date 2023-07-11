package io.airlift.tracing;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;

public class TestOpenTelemetryConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(OpenTelemetryConfig.class)
                .setEndpoint("http://localhost:4317")
                .setMaxBatchSize(512)
                .setMaxQueueSize(2048));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("tracing.exporter.endpoint", "http://example.com:1234")
                .put("tracing.exporter.max-batch-size", "256")
                .put("tracing.exporter.max-queue-size", "512")
                .buildOrThrow();

        OpenTelemetryConfig expected = new OpenTelemetryConfig()
                .setEndpoint("http://example.com:1234")
                .setMaxBatchSize(256)
                .setMaxQueueSize(512);

        assertFullMapping(properties, expected);
    }
}
