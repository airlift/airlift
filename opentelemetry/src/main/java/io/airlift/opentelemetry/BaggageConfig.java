package io.airlift.opentelemetry;

import com.google.common.collect.ImmutableSet;
import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import jakarta.validation.constraints.Min;

import java.util.Set;

public class BaggageConfig
{
    private Set<String> allowedKeys = ImmutableSet.of();
    private int maxValueLength = 2048;

    public Set<String> getAllowedKeys()
    {
        return allowedKeys;
    }

    @Config("otel.tracing.baggage.allowed-keys")
    @ConfigDescription("Comma-separated list of baggage keys allowed to propagate; all other keys are dropped")
    public BaggageConfig setAllowedKeys(Set<String> allowedKeys)
    {
        this.allowedKeys = ImmutableSet.copyOf(allowedKeys);
        return this;
    }

    @Min(0)
    public int getMaxValueLength()
    {
        return maxValueLength;
    }

    @Config("otel.tracing.baggage.max-value-length")
    @ConfigDescription("Maximum length of an allowed baggage value; longer values are truncated")
    public BaggageConfig setMaxValueLength(int maxValueLength)
    {
        this.maxValueLength = maxValueLength;
        return this;
    }
}
