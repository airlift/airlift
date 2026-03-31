package io.airlift.opentelemetry;

import io.airlift.configuration.Config;
import io.airlift.configuration.LegacyConfig;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Optional;

public class OpenTelemetryConfig
{
    private double samplingRatio = 1;
    private Optional<@Min(1) Integer> maxAttributeValueLength = Optional.empty();

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

    public Optional<@Min(1) Integer> getMaxAttributeValueLength()
    {
        return maxAttributeValueLength;
    }

    @Config("otel.tracing.max-attribute-value-length")
    public OpenTelemetryConfig setMaxAttributeValueLength(Integer maxAttributeValueLength)
    {
        this.maxAttributeValueLength = Optional.ofNullable(maxAttributeValueLength);
        return this;
    }
}
