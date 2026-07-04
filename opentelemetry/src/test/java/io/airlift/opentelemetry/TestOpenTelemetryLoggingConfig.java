package io.airlift.opentelemetry;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;

public class TestOpenTelemetryLoggingConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(OpenTelemetryLoggingConfig.class)
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

        assertFullMapping(properties, expected);
    }
}
