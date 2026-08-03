package io.airlift.opentelemetry;

import io.airlift.configuration.Config;

public class OpenTelemetryLoggingConfig
{
    private boolean enabled;

    public boolean isEnabled()
    {
        return enabled;
    }

    @Config("log.otlp.enabled")
    public OpenTelemetryLoggingConfig setEnabled(boolean enabled)
    {
        this.enabled = enabled;
        return this;
    }
}
