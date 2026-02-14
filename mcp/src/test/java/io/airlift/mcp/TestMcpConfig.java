package io.airlift.mcp;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import io.airlift.units.Duration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TestMcpConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(ConfigAssertions.recordDefaults(McpConfig.class)
                .setDefaultPageSize(25)
                .setDefaultSessionTimeout(new Duration(15, MINUTES))
                .setHttpGetEventsEnabled(true)
                .setEventStreamingPingThreshold(new Duration(15, SECONDS))
                .setEventStreamingTimeout(new Duration(5, MINUTES))
                .setCancellationCheckInterval(new Duration(1, SECONDS))
                .setMaxResumableMessages(100)
                .setMaxSessionCache(10000)
                .setResourceSubscriptionCachePeriod(new Duration(1, MINUTES))
                .setDefaultTaskTtl(new Duration(1, HOURS))
                .setAbandonedTaskThreshold(new Duration(7, DAYS)));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("mcp.page-size", "2468")
                .put("mcp.session.timeout", "1d")
                .put("mcp.http-get-events.enabled", "false")
                .put("mcp.event-streaming.ping-threshold", "123m")
                .put("mcp.event-streaming.timeout", "456m")
                .put("mcp.cancellation.check-interval", "1h")
                .put("mcp.resumable-messages.max", "962")
                .put("mcp.session.cache.max-size", "10064")
                .put("mcp.resource-subscription.cache-period", "123m")
                .put("mcp.task.default-ttl", "5678m")
                .put("mcp.task.abandoned-threshold", "8h")
                .build();

        McpConfig expected = new McpConfig()
                .setDefaultPageSize(2468)
                .setDefaultSessionTimeout(new Duration(1, DAYS))
                .setHttpGetEventsEnabled(false)
                .setEventStreamingPingThreshold(new Duration(123, MINUTES))
                .setEventStreamingTimeout(new Duration(456, MINUTES))
                .setCancellationCheckInterval(new Duration(1, HOURS))
                .setMaxResumableMessages(962)
                .setMaxSessionCache(10064)
                .setResourceSubscriptionCachePeriod(new Duration(123, MINUTES))
                .setDefaultTaskTtl(new Duration(5678, MINUTES))
                .setAbandonedTaskThreshold(new Duration(8, HOURS));

        assertFullMapping(properties, expected);
    }
}
