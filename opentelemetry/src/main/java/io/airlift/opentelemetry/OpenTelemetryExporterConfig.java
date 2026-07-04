package io.airlift.opentelemetry;

import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigSecuritySensitive;
import io.airlift.configuration.LegacyConfig;
import io.airlift.configuration.validation.FileExists;
import io.airlift.units.Duration;
import io.airlift.units.MinDuration;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class OpenTelemetryExporterConfig
{
    private URI endpoint = URI.create("http://localhost:4317");
    private Protocol protocol = Protocol.GRPC;
    private Duration interval = new Duration(1, TimeUnit.MINUTES);
    private Optional<Integer> spanMaxExportBatchSize = Optional.empty();
    private Optional<Integer> spanMaxQueueSize = Optional.empty();
    private Optional<Duration> spanScheduleDelay = Optional.empty();
    private Optional<Integer> logMaxExportBatchSize = Optional.empty();
    private Optional<Integer> logMaxQueueSize = Optional.empty();
    private Optional<Duration> logScheduleDelay = Optional.empty();
    private Optional<Path> trustedCertificatesPath = Optional.empty();
    private Optional<String> trustedCertificatesPem = Optional.empty();
    private Optional<Path> clientCertificatePath = Optional.empty();
    private Optional<String> clientCertificatePem = Optional.empty();
    private Optional<Path> clientKeyPath = Optional.empty();
    private Optional<String> clientKeyPem = Optional.empty();
    private Optional<String> clientKeyPassword = Optional.empty();

    @NotNull
    public URI getEndpoint()
    {
        return endpoint;
    }

    @Config("otel.exporter.endpoint")
    @LegacyConfig("tracing.exporter.endpoint")
    public OpenTelemetryExporterConfig setEndpoint(URI endpoint)
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

    public Optional<String> getTrustedCertificatesPem()
    {
        return trustedCertificatesPem;
    }

    @Config("otel.exporter.tls.trusted-certificates-pem")
    public OpenTelemetryExporterConfig setTrustedCertificatesPem(String trustedCertificatesPem)
    {
        this.trustedCertificatesPem = Optional.ofNullable(trustedCertificatesPem);
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

    public Optional<String> getClientCertificatePem()
    {
        return clientCertificatePem;
    }

    @Config("otel.exporter.tls.client-certificate-pem")
    public OpenTelemetryExporterConfig setClientCertificatePem(String clientCertificatePem)
    {
        this.clientCertificatePem = Optional.ofNullable(clientCertificatePem);
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

    public Optional<String> getClientKeyPem()
    {
        return clientKeyPem;
    }

    @Config("otel.exporter.tls.client-key-pem")
    @ConfigSecuritySensitive
    public OpenTelemetryExporterConfig setClientKeyPem(String clientKeyPem)
    {
        this.clientKeyPem = Optional.ofNullable(clientKeyPem);
        return this;
    }

    public Optional<String> getClientKeyPassword()
    {
        return clientKeyPassword;
    }

    @Config("otel.exporter.tls.client-key-password")
    @ConfigSecuritySensitive
    public OpenTelemetryExporterConfig setClientKeyPassword(String clientKeyPassword)
    {
        this.clientKeyPassword = Optional.ofNullable(clientKeyPassword);
        return this;
    }

    @AssertTrue(message = "client certificate and key must be set together")
    public boolean isClientTlsValid()
    {
        return hasClientCertificate() == hasClientKey();
    }

    @AssertTrue(message = "must start with http:// or https://")
    public boolean isEndpointProtocolValid()
    {
        return endpoint == null || "http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme());
    }

    @AssertTrue(message = "client key password requires client key")
    public boolean isClientKeyPasswordValid()
    {
        return clientKeyPassword.isEmpty() || hasClientKey();
    }

    @AssertTrue(message = "trusted certificates path and PEM cannot both be set")
    public boolean isTrustedCertificatesSourceValid()
    {
        return trustedCertificatesPath.isEmpty() || trustedCertificatesPem.isEmpty();
    }

    @AssertTrue(message = "client certificate path and PEM cannot both be set")
    public boolean isClientCertificateSourceValid()
    {
        return clientCertificatePath.isEmpty() || clientCertificatePem.isEmpty();
    }

    @AssertTrue(message = "client key path and PEM cannot both be set")
    public boolean isClientKeySourceValid()
    {
        return clientKeyPath.isEmpty() || clientKeyPem.isEmpty();
    }

    private boolean hasClientCertificate()
    {
        return clientCertificatePath.isPresent() || clientCertificatePem.isPresent();
    }

    private boolean hasClientKey()
    {
        return clientKeyPath.isPresent() || clientKeyPem.isPresent();
    }
}
