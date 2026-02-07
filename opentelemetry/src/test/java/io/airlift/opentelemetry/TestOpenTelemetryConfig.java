package io.airlift.opentelemetry;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestOpenTelemetryConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(OpenTelemetryConfig.class)
                .setSamplingRatio(1.0)
                .setResourceAttributes(""));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("otel.tracing.sampling-ratio", "0.2")
                .put("otel.resource.attributes", "custom.atribute=value1,another.attribute=value2")
                .buildOrThrow();

        OpenTelemetryConfig expected = new OpenTelemetryConfig()
                .setSamplingRatio(0.2)
                .setResourceAttributes("custom.atribute=value1,another.attribute=value2");

        assertFullMapping(properties, expected);
    }

    @Test
    public void testResourceAttributesParsing()
    {
        OpenTelemetryConfig config = new OpenTelemetryConfig();

        // Test with null value
        config.setResourceAttributes(null);
        assertThat(config.getResourceAttributes()).isEmpty();

        // Test with empty string
        config = new OpenTelemetryConfig();
        config.setResourceAttributes("");
        assertThat(config.getResourceAttributes()).isEmpty();

        // Test with single attribute
        config = new OpenTelemetryConfig();
        config.setResourceAttributes("custom.atribute=value1");
        assertThat(config.getResourceAttributes())
                .containsEntry("custom.atribute", "value1");

        // Test with multiple attributes
        config = new OpenTelemetryConfig();
        config.setResourceAttributes("custom.atribute=value1,another.attribute=value2");
        assertThat(config.getResourceAttributes())
                .containsEntry("custom.atribute", "value1")
                .containsEntry("another.attribute", "value2")
                .hasSize(2);

        // Test with whitespace around key-value pairs
        config = new OpenTelemetryConfig();
        config.setResourceAttributes("custom.atribute = value1 , another.attribute = value2");
        assertThat(config.getResourceAttributes())
                .containsEntry("custom.atribute ", " value1")
                .containsEntry("another.attribute ", " value2")
                .hasSize(2);

        // Test setting with invalid format (no '=' sign) - should throw an exception
        OpenTelemetryConfig invalidConfig = new OpenTelemetryConfig();
        assertThatThrownBy(() -> {
            invalidConfig.setResourceAttributes("custom.atribute=value1,invalidentry,another.attribute=value2");
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testDefaultResourceAttributes()
    {
        OpenTelemetryConfig config = new OpenTelemetryConfig();
        assertThat(config.getResourceAttributes()).isEqualTo(Collections.emptyMap());
    }
}
