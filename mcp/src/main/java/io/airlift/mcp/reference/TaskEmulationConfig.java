package io.airlift.mcp.reference;

import io.airlift.configuration.Config;
import io.airlift.units.Duration;
import io.airlift.units.MinDuration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

public class TaskEmulationConfig
{
    private Duration defaultPollingInterval = new Duration(500, MILLISECONDS);
    private Duration emulationHandlerTimeout = new Duration(5, MINUTES);
    private Duration taskTtl = new Duration(5, MINUTES);

    @MinDuration("1ms")
    public Duration getDefaultPollingInterval()
    {
        return defaultPollingInterval;
    }

    @Config("mcp.task-emulation.default-polling-interval")
    public TaskEmulationConfig setDefaultPollingInterval(Duration defaultPollingInterval)
    {
        this.defaultPollingInterval = defaultPollingInterval;
        return this;
    }

    @MinDuration("1ms")
    public Duration getEmulationHandlerTimeout()
    {
        return emulationHandlerTimeout;
    }

    @Config("mcp.task-emulation.handler-timeout")
    public TaskEmulationConfig setEmulationHandlerTimeout(Duration emulationHandlerTimeout)
    {
        this.emulationHandlerTimeout = emulationHandlerTimeout;
        return this;
    }

    @MinDuration("1ms")
    public Duration getTaskTtl()
    {
        return taskTtl;
    }

    @Config("mcp.task-emulation.task-ttl")
    public TaskEmulationConfig setTaskTtl(Duration taskTtl)
    {
        this.taskTtl = taskTtl;
        return this;
    }
}
