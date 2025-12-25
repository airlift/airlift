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

public class TestMcpConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(ConfigAssertions.recordDefaults(McpConfig.class)
                .setDefaultPageSize(25)
                .setDefaultSessionTimeout(new Duration(15, MINUTES))
                .setResourceVersionUpdateInterval(new Duration(5, MINUTES)));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("mcp.page-size", "2468")
                .put("mcp.session.timeout", "1d")
                .put("mcp.resource-version.update-interval", "12d")
                .build();

        McpConfig expected = new McpConfig()
                .setDefaultPageSize(2468)
                .setDefaultSessionTimeout(new Duration(1, DAYS))
                .setResourceVersionUpdateInterval(new Duration(12, DAYS));

        assertFullMapping(properties, expected);
    }
}
