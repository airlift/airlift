package io.airlift.opentelemetry;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestOpenTelemetryConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(OpenTelemetryConfig.class)
                .setSamplingRatio(1.0)
                .setMaxAttributeValueLength(null)
                .setSpanFilterDrop(ImmutableList.of()));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("otel.tracing.sampling-ratio", "0.2")
                .put("otel.tracing.max-attribute-value-length", "8192")
                .put("otel.tracing.span-filter.drop", "trino=>process, GET /v1/task/*/results/*;children=drop")
                .buildOrThrow();

        OpenTelemetryConfig expected = new OpenTelemetryConfig()
                .setSamplingRatio(0.2)
                .setMaxAttributeValueLength(8192)
                .setSpanFilterDrop(ImmutableList.of("trino=>process", "GET /v1/task/*/results/*;children=drop"));

        assertFullMapping(properties, expected);
    }

    @Test
    public void testInvalidSpanFilterRuleRejected()
    {
        assertThatThrownBy(() -> new OpenTelemetryConfig().setSpanFilterDrop(ImmutableList.of("process", "process;children=keep")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid children mode 'keep' in span filter rule: process;children=keep");
    }
}
