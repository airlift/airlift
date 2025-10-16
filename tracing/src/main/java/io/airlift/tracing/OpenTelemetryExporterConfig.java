package io.airlift.tracing;

import io.airlift.configuration.Config;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class OpenTelemetryExporterConfig {
    private String endpoint = "http://localhost:4317";
    private Protocol protocol = Protocol.GRPC;

    @NotNull
    @Pattern(regexp = "^(http|https)://.*$", message = "must start with http:// or https://")
    public String getEndpoint() {
        return endpoint;
    }

    @Config("tracing.exporter.endpoint")
    public OpenTelemetryExporterConfig setEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    @NotNull
    public Protocol getProtocol() {
        return protocol;
    }

    @Config("tracing.exporter.protocol")
    public OpenTelemetryExporterConfig setProtocol(Protocol protocol) {
        this.protocol = protocol;
        return this;
    }

    public enum Protocol {
        GRPC,
        HTTP_PROTOBUF;

        public static Protocol fromString(String protocol) {
            return switch (protocol) {
                case "grpc" -> GRPC;
                case "http/protobuf" -> HTTP_PROTOBUF;
                default -> throw new IllegalArgumentException("Invalid protocol: " + protocol);
            };
        }
    }
}
