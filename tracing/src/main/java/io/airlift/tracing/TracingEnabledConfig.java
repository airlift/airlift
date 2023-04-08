package io.airlift.tracing;

import io.airlift.configuration.Config;

public class TracingEnabledConfig
{
    private boolean enabled;

    public boolean isEnabled()
    {
        return enabled;
    }

    @Config("tracing.enabled")
    public TracingEnabledConfig setEnabled(boolean enabled)
    {
        this.enabled = enabled;
        return this;
    }
}
