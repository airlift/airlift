package io.airlift.opentelemetry;

import com.google.common.base.Splitter;
import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import io.airlift.configuration.LegacyConfig;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class OpenTelemetryConfig
{
    private static final Splitter.MapSplitter MAP_SPLITTER = Splitter.on(",").trimResults().omitEmptyStrings().withKeyValueSeparator("=");

    private Map<String, String> resourceAttributes = Collections.emptyMap();
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

    @NotNull
    public Map<String, String> getResourceAttributes()
    {
        return resourceAttributes;
    }

    @Config("otel.resource.attributes")
    @ConfigDescription("Comma-separated list of key-value pairs to specify custom resource attributes. Eg. 'custom.attribute=value1,another.attribute=value2")
    public OpenTelemetryConfig setResourceAttributes(String attributes)
    {
        this.resourceAttributes = Optional.ofNullable(attributes).map(MAP_SPLITTER::split).orElse(Collections.emptyMap());
        return this;
    }
}
