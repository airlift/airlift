package io.airlift.mcp;

import io.airlift.configuration.Config;
import io.airlift.units.Duration;
import io.airlift.units.MaxDuration;
import io.airlift.units.MinDuration;
import jakarta.validation.constraints.Min;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class McpConfig
{
    private int defaultPageSize = 25;
    private Duration defaultSessionTimeout = new Duration(15, MINUTES);
    private Duration resourceVersionUpdateInterval = new Duration(5, MINUTES);
    private boolean httpGetEventsEnabled = true;
    private Duration eventStreamingPingThreshold = new Duration(15, SECONDS);
    private Duration eventStreamingTimeout = new Duration(5, MINUTES);
    private Duration cancellationCheckInterval = new Duration(1, SECONDS);
    private int maxResumableMessages = 100;
    private Duration defaultTaskTtl = new Duration(1, HOURS);
    private Duration taskCleanupInterval = new Duration(15, MINUTES);
    private Duration abandonedTaskThreshold = new Duration(7, DAYS);

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
    public Duration getDefaultSessionTimeout()
    {
        return defaultSessionTimeout;
    }

    @Config("mcp.session.timeout")
    public McpConfig setDefaultSessionTimeout(Duration defaultSessionTimeout)
    {
        this.defaultSessionTimeout = defaultSessionTimeout;
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

    public boolean isHttpGetEventsEnabled()
    {
        return httpGetEventsEnabled;
    }

    @Config("mcp.http-get-events.enabled")
    public McpConfig setHttpGetEventsEnabled(boolean httpGetEventsEnabled)
    {
        this.httpGetEventsEnabled = httpGetEventsEnabled;
        return this;
    }

    @MinDuration("1ms")
    public Duration getEventStreamingPingThreshold()
    {
        return eventStreamingPingThreshold;
    }

    @Config("mcp.event-streaming.ping-threshold")
    public McpConfig setEventStreamingPingThreshold(Duration eventStreamingPingThreshold)
    {
        this.eventStreamingPingThreshold = eventStreamingPingThreshold;
        return this;
    }

    @MinDuration("1ms")
    public Duration getEventStreamingTimeout()
    {
        return eventStreamingTimeout;
    }

    @Config("mcp.event-streaming.timeout")
    public McpConfig setEventStreamingTimeout(Duration eventStreamingTimeout)
    {
        this.eventStreamingTimeout = eventStreamingTimeout;
        return this;
    }

    @MinDuration("1ms")
    public Duration getCancellationCheckInterval()
    {
        return cancellationCheckInterval;
    }

    @Config("mcp.cancellation.check-interval")
    public McpConfig setCancellationCheckInterval(Duration cancellationCheckInterval)
    {
        this.cancellationCheckInterval = cancellationCheckInterval;
        return this;
    }

    @Min(0)
    public int getMaxResumableMessages()
    {
        return maxResumableMessages;
    }

    @Config("mcp.resumable-messages.max")
    public McpConfig setMaxResumableMessages(int maxResumableMessages)
    {
        this.maxResumableMessages = maxResumableMessages;
        return this;
    }

    @MinDuration("1ms")
    @MaxDuration(Integer.MAX_VALUE + "ms")
    public Duration getDefaultTaskTtl()
    {
        return defaultTaskTtl;
    }

    @Config("mcp.task.default-ttl")
    public McpConfig setDefaultTaskTtl(Duration defaultTaskTtl)
    {
        this.defaultTaskTtl = defaultTaskTtl;
        return this;
    }

    @MinDuration("1ms")
    public Duration getTaskCleanupInterval()
    {
        return taskCleanupInterval;
    }

    @Config("mcp.task.cleanup-interval")
    public McpConfig setTaskCleanupInterval(Duration taskCleanupInterval)
    {
        this.taskCleanupInterval = taskCleanupInterval;
        return this;
    }

    @MinDuration("1ms")
    public Duration getAbandonedTaskThreshold()
    {
        return abandonedTaskThreshold;
    }

    @Config("mcp.task.abandoned-threshold")
    public McpConfig setAbandonedTaskThreshold(Duration abandonedTaskThreshold)
    {
        this.abandonedTaskThreshold = abandonedTaskThreshold;
        return this;
    }
}
