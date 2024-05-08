package io.airlift.tracing;

import io.airlift.configuration.Config;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class OpenTelemetryExporterConfig
{
    private String endpoint = "http://localhost:4317";

    @NotNull
    @Pattern(regexp = "^(http|https)://.*$", message = "must start with http:// or https://")
    public String getEndpoint()
    {
        return endpoint;
    }

    @Config("tracing.exporter.endpoint")
    public OpenTelemetryExporterConfig setEndpoint(String endpoint)
    {
        this.endpoint = endpoint;
        return this;
    }
}
