package io.airlift.tracing;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TestTracingEnabledConfig {
    @Test
    public void testDefaults() {
        assertRecordedDefaults(recordDefaults(TracingEnabledConfig.class).setEnabled(false));
    }

    @Test
    public void testExplicitPropertyMappings() {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("tracing.enabled", "true")
                .buildOrThrow();

        TracingEnabledConfig expected = new TracingEnabledConfig().setEnabled(true);

        assertFullMapping(properties, expected);
    }
}
