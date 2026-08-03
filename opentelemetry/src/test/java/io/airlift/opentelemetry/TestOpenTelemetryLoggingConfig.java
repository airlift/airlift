package io.airlift.opentelemetry;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class TestOpenTelemetryLoggingConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(OpenTelemetryLoggingConfig.class)
                .setEnabled(false));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("log.otlp.enabled", "true")
                .buildOrThrow();

        OpenTelemetryLoggingConfig expected = new OpenTelemetryLoggingConfig()
                .setEnabled(true);

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
