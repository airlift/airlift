package io.airlift.opentelemetry;

import io.airlift.configuration.Config;
import io.airlift.configuration.LegacyConfig;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class OpenTelemetryConfig
{
    private double samplingRatio = 1;

    @Max(1)
    @Min(0)
    public double getSamplingRatio()
    {
        return samplingRatio;
    }

    @Config("otel.tracing.sampling-ratio")
    @LegacyConfig("tracing.sampling-ratio")
    public OpenTelemetryConfig setSamplingRatio(Double ratio)
    {
        this.samplingRatio = ratio;
        return this;
    }
}
