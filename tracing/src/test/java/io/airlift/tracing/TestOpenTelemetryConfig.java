package io.airlift.tracing;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TestOpenTelemetryConfig {
    @Test
    public void testDefaults() {
        assertRecordedDefaults(recordDefaults(OpenTelemetryConfig.class).setSamplingRatio(1.0));
    }

    @Test
    public void testExplicitPropertyMappings() {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("tracing.sampling-ratio", "0.2")
                .buildOrThrow();

        OpenTelemetryConfig expected = new OpenTelemetryConfig().setSamplingRatio(0.2);

        assertFullMapping(properties, expected);
    }
}
