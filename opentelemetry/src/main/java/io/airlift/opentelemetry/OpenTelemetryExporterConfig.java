package io.airlift.opentelemetry;

import io.airlift.configuration.Config;
import io.airlift.configuration.LegacyConfig;
import io.airlift.configuration.validation.FileExists;
import io.airlift.units.Duration;
import io.airlift.units.MinDuration;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class OpenTelemetryExporterConfig
{
    private String endpoint = "http://localhost:4317";
    private Protocol protocol = Protocol.GRPC;
    private Duration interval = new Duration(1, TimeUnit.MINUTES);
    private Optional<Integer> spanMaxExportBatchSize = Optional.empty();
    private Optional<Integer> spanMaxQueueSize = Optional.empty();
    private Optional<Duration> spanScheduleDelay = Optional.empty();
    private Optional<Integer> logMaxExportBatchSize = Optional.empty();
    private Optional<Integer> logMaxQueueSize = Optional.empty();
    private Optional<Duration> logScheduleDelay = Optional.empty();
    private Optional<Path> trustedCertificatesPath = Optional.empty();
    private Optional<Path> clientCertificatePath = Optional.empty();
    private Optional<Path> clientKeyPath = Optional.empty();

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

    public Optional<@Min(1) Integer> getSpanMaxExportBatchSize()
    {
        return spanMaxExportBatchSize;
    }

    @Config("otel.exporter.span.max-export-batch-size")
    @LegacyConfig("otel.exporter.max-export-batch-size")
    public OpenTelemetryExporterConfig setSpanMaxExportBatchSize(Integer spanMaxExportBatchSize)
    {
        this.spanMaxExportBatchSize = Optional.ofNullable(spanMaxExportBatchSize);
        return this;
    }

    public Optional<@Min(1) Integer> getSpanMaxQueueSize()
    {
        return spanMaxQueueSize;
    }

    @Config("otel.exporter.span.max-queue-size")
    public OpenTelemetryExporterConfig setSpanMaxQueueSize(Integer spanMaxQueueSize)
    {
        this.spanMaxQueueSize = Optional.ofNullable(spanMaxQueueSize);
        return this;
    }

    public Optional<@MinDuration("1ms") Duration> getSpanScheduleDelay()
    {
        return spanScheduleDelay;
    }

    @Config("otel.exporter.span.schedule-delay")
    public OpenTelemetryExporterConfig setSpanScheduleDelay(Duration spanScheduleDelay)
    {
        this.spanScheduleDelay = Optional.ofNullable(spanScheduleDelay);
        return this;
    }

    public Optional<@Min(1) Integer> getLogMaxExportBatchSize()
    {
        return logMaxExportBatchSize;
    }

    @Config("otel.exporter.log.max-export-batch-size")
    public OpenTelemetryExporterConfig setLogMaxExportBatchSize(Integer logMaxExportBatchSize)
    {
        this.logMaxExportBatchSize = Optional.ofNullable(logMaxExportBatchSize);
        return this;
    }

    public Optional<@Min(1) Integer> getLogMaxQueueSize()
    {
        return logMaxQueueSize;
    }

    @Config("otel.exporter.log.max-queue-size")
    public OpenTelemetryExporterConfig setLogMaxQueueSize(Integer logMaxQueueSize)
    {
        this.logMaxQueueSize = Optional.ofNullable(logMaxQueueSize);
        return this;
    }

    public Optional<@MinDuration("1ms") Duration> getLogScheduleDelay()
    {
        return logScheduleDelay;
    }

    @Config("otel.exporter.log.schedule-delay")
    public OpenTelemetryExporterConfig setLogScheduleDelay(Duration logScheduleDelay)
    {
        this.logScheduleDelay = Optional.ofNullable(logScheduleDelay);
        return this;
    }

    public Optional<@FileExists Path> getTrustedCertificatesPath()
    {
        return trustedCertificatesPath;
    }

    @Config("otel.exporter.tls.trusted-certificates-path")
    public OpenTelemetryExporterConfig setTrustedCertificatesPath(Path trustedCertificatesPath)
    {
        this.trustedCertificatesPath = Optional.ofNullable(trustedCertificatesPath);
        return this;
    }

    public Optional<@FileExists Path> getClientCertificatePath()
    {
        return clientCertificatePath;
    }

    @Config("otel.exporter.tls.client-certificate-path")
    public OpenTelemetryExporterConfig setClientCertificatePath(Path clientCertificatePath)
    {
        this.clientCertificatePath = Optional.ofNullable(clientCertificatePath);
        return this;
    }

    public Optional<@FileExists Path> getClientKeyPath()
    {
        return clientKeyPath;
    }

    @Config("otel.exporter.tls.client-key-path")
    public OpenTelemetryExporterConfig setClientKeyPath(Path clientKeyPath)
    {
        this.clientKeyPath = Optional.ofNullable(clientKeyPath);
        return this;
    }

    @AssertTrue(message = "client certificate and key paths must be set together")
    public boolean isClientTlsValid()
    {
        return clientCertificatePath.isPresent() == clientKeyPath.isPresent();
    }
}
