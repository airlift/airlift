package io.airlift.mcp;

import io.airlift.configuration.Config;
import io.airlift.units.Duration;
import io.airlift.units.MinDuration;
import jakarta.validation.constraints.Min;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class McpConfig
{
    private int defaultPageSize = 25;
    private Duration defaultSessionTimeout = new Duration(15, MINUTES);
    private boolean httpGetEventsEnabled = true;
    private Duration eventStreamingPingThreshold = new Duration(15, SECONDS);
    private Duration eventStreamingTimeout = new Duration(5, MINUTES);
    private Duration cancellationCheckInterval = new Duration(1, SECONDS);
    private int maxResumableMessages = 100;
    private int maxSessionCache = 10000;

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

    @Min(1)
    public int getMaxSessionCache()
    {
        return maxSessionCache;
    }

    @Config("mcp.session.cache.max-size")
    public McpConfig setMaxSessionCache(int maxSessionCache)
    {
        this.maxSessionCache = maxSessionCache;
        return this;
    }
}
