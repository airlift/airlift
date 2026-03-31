package io.airlift.opentelemetry;

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
                .setSamplingRatio(1.0)
                .setMaxAttributeValueLength(null));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("otel.tracing.sampling-ratio", "0.2")
                .put("otel.tracing.max-attribute-value-length", "8192")
                .buildOrThrow();

        OpenTelemetryConfig expected = new OpenTelemetryConfig()
                .setSamplingRatio(0.2)
                .setMaxAttributeValueLength(8192);

        assertFullMapping(properties, expected);
    }
}
