package io.airlift.mcp.reference;

import io.airlift.configuration.Config;
import io.airlift.units.Duration;
import io.airlift.units.MinDuration;

public class ReferenceServerConfig
{
    private Duration eventLoopMaxDuration = Duration.valueOf("15m");
    private Duration sessionPingInterval = Duration.valueOf("10s");

    @MinDuration("1ms")
    public Duration getEventLoopMaxDuration()
    {
        return eventLoopMaxDuration;
    }

    @Config("mcp.reference.event-loop-max-duration")
    public ReferenceServerConfig setEventLoopMaxDuration(Duration eventLoopMaxDuration)
    {
        this.eventLoopMaxDuration = eventLoopMaxDuration;
        return this;
    }

    @MinDuration("1ms")
    public Duration getSessionPingInterval()
    {
        return sessionPingInterval;
    }

    @Config("mcp.reference.session-ping-interval")
    public ReferenceServerConfig setSessionPingInterval(Duration sessionPingInterval)
    {
        this.sessionPingInterval = sessionPingInterval;
        return this;
    }
}
