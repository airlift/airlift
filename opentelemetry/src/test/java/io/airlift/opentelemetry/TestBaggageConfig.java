package io.airlift.opentelemetry;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;
import static org.assertj.core.api.Assertions.assertThat;

public class TestBaggageConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(BaggageConfig.class)
                .setAllowedKeys(ImmutableSet.of())
                .setMaxValueLength(2048));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("otel.tracing.baggage.allowed-keys", "orderId,tenant")
                .put("otel.tracing.baggage.max-value-length", "512")
                .buildOrThrow();

        BaggageConfig expected = new BaggageConfig()
                .setAllowedKeys(ImmutableSet.of("orderId", "tenant"))
                .setMaxValueLength(512);

        assertFullMapping(properties, expected);

        assertThat(expected.getAllowedKeys()).isEqualTo(ImmutableSet.of("orderId", "tenant"));
    }
}
