package io.airlift.opentelemetry;

import com.google.common.collect.ImmutableList;
import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import io.airlift.configuration.LegacyConfig;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Optional;

public class OpenTelemetryConfig
{
    private double samplingRatio = 1;
    private Optional<@Min(1) Integer> maxAttributeValueLength = Optional.empty();
    private List<String> spanFilterDrop = ImmutableList.of();

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

    @NotNull
    public List<String> getSpanFilterDrop()
    {
        return spanFilterDrop;
    }

    @Config("otel.tracing.span-filter.drop")
    @ConfigDescription("Spans to drop, as comma-separated [scope-glob=>]name-glob[;children=reparent|drop] entries matched against the name a span is created with")
    public OpenTelemetryConfig setSpanFilterDrop(List<String> spanFilterDrop)
    {
        SpanFilterRule.parseAll(spanFilterDrop);
        this.spanFilterDrop = ImmutableList.copyOf(spanFilterDrop);
        return this;
    }
}
