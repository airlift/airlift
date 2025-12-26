package io.airlift.mcp;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import io.airlift.units.Duration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TestMcpConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(ConfigAssertions.recordDefaults(McpConfig.class)
                .setDefaultPageSize(25)
                .setResourceVersionUpdateInterval(new Duration(5, MINUTES))
                .setHttpGetEventsEnabled(true)
                .setEventStreamingPingThreshold(new Duration(15, SECONDS))
                .setEventStreamingTimeout(new Duration(5, MINUTES)));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("mcp.page-size", "2468")
                .put("mcp.resource-version.update-interval", "12d")
                .put("mcp.http-get-events.enabled", "false")
                .put("mcp.event-streaming.ping-threshold", "123m")
                .put("mcp.event-streaming.timeout", "456m")
                .build();

        McpConfig expected = new McpConfig()
                .setDefaultPageSize(2468)
                .setResourceVersionUpdateInterval(new Duration(12, DAYS))
                .setHttpGetEventsEnabled(false)
                .setEventStreamingPingThreshold(new Duration(123, MINUTES))
                .setEventStreamingTimeout(new Duration(456, MINUTES));

        assertFullMapping(properties, expected);
    }
}
