package io.airlift.mcp.session.memory;

import io.airlift.configuration.Config;
import io.airlift.units.Duration;
import io.airlift.units.MinDuration;

public class MemorySessionConfig
{
    private Duration sessionTimeout = Duration.valueOf("15m");

    @MinDuration("1ms")
    public Duration getSessionTimeout()
    {
        return sessionTimeout;
    }

    @Config("mcp.memory-session.timeout")
    public MemorySessionConfig setSessionTimeout(Duration sessionTimeout)
    {
        this.sessionTimeout = sessionTimeout;
        return this;
    }
}
