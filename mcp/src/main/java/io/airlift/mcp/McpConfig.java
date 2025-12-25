package io.airlift.mcp;

import io.airlift.configuration.Config;
import io.airlift.units.Duration;
import io.airlift.units.MinDuration;
import jakarta.validation.constraints.Min;

import static java.util.concurrent.TimeUnit.MINUTES;

public class McpConfig
{
    private int defaultPageSize = 25;
    private Duration resourceVersionUpdateInterval = new Duration(5, MINUTES);

    @Min(1)
    public int getDefaultPageSize()
    {
        return defaultPageSize;
    }

    @Config("mcp.page-size")
    public McpConfig setDefaultPageSize(int defaultPageSize)
    {
        this.defaultPageSize = defaultPageSize;
        return this;
    }

    @MinDuration("1ms")
    public Duration getResourceVersionUpdateInterval()
    {
        return resourceVersionUpdateInterval;
    }

    @Config("mcp.resource-version.update-interval")
    public McpConfig setResourceVersionUpdateInterval(Duration resourceVersionUpdateInterval)
    {
        this.resourceVersionUpdateInterval = resourceVersionUpdateInterval;
        return this;
    }
}
