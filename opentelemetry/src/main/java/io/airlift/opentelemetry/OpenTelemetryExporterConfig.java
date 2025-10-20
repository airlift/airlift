package io.airlift.opentelemetry;

import io.airlift.configuration.Config;
import io.airlift.configuration.LegacyConfig;
import io.airlift.units.Duration;
import io.airlift.units.MinDuration;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.concurrent.TimeUnit;

public class OpenTelemetryExporterConfig
{
    private String endpoint = "http://localhost:4317";
    private Protocol protocol = Protocol.GRPC;
    private Duration interval = new Duration(1, TimeUnit.MINUTES);

    @NotNull
    @Pattern(regexp = "^(http|https)://.*$", message = "must start with http:// or https://")
    public String getEndpoint()
    {
        return endpoint;
    }

    @Config("otel.exporter.endpoint")
    @LegacyConfig("tracing.exporter.endpoint")
    public OpenTelemetryExporterConfig setEndpoint(String endpoint)
    {
        this.endpoint = endpoint;
        return this;
    }

    @NotNull
    public Protocol getProtocol()
    {
        return protocol;
    }

    @Config("otel.exporter.protocol")
    @LegacyConfig("tracing.exporter.protocol")
    public OpenTelemetryExporterConfig setProtocol(Protocol protocol)
    {
        this.protocol = protocol;
        return this;
    }

    public enum Protocol
    {
        GRPC,
        HTTP_PROTOBUF;

        public static Protocol fromString(String protocol)
        {
            return switch (protocol) {
                case "grpc" -> GRPC;
                case "http/protobuf" -> HTTP_PROTOBUF;
                default -> throw new IllegalArgumentException("Invalid protocol: " + protocol);
            };
        }
    }

    @NotNull
    @MinDuration("1s")
    public Duration getInterval()
    {
        return interval;
    }

    @Config("otel.exporter.interval")
    public OpenTelemetryExporterConfig setInterval(Duration interval)
    {
        this.interval = interval;
        return this;
    }
}
