package io.airlift.opentelemetry;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.multibindings.ProvidesIntoSet;
import io.airlift.security.pem.PemReader;
import io.airlift.security.pem.PemWriter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporterBuilder;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporterBuilder;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.common.InternalTelemetryVersion;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessorBuilder;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessorBuilder;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Optional;
import java.util.function.BiConsumer;

import static io.airlift.configuration.ConfigBinder.configBinder;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.file.Files.readAllBytes;

public class OpenTelemetryExporterModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        configBinder(binder).bindConfig(OpenTelemetryExporterConfig.class);
    }

    @ProvidesIntoSet
    public static SpanProcessor createSpanProcessor(OpenTelemetryExporterConfig config, SdkMeterProvider meterProvider)
    {
        BatchSpanProcessorBuilder builder = BatchSpanProcessor.builder(createSpanExporter(config))
                .setMeterProvider(meterProvider);
        config.getSpanMaxExportBatchSize().ifPresent(builder::setMaxExportBatchSize);
        config.getSpanMaxQueueSize().ifPresent(builder::setMaxQueueSize);
        config.getSpanScheduleDelay().ifPresent(delay -> builder.setScheduleDelay(delay.toJavaTime()));
        builder.setInternalTelemetryVersion(InternalTelemetryVersion.LATEST);
        return builder.build();
    }

    static SpanExporter createSpanExporter(OpenTelemetryExporterConfig config)
    {
        return switch (config.getProtocol()) {
            case GRPC -> configureTls(
                    config,
                    OtlpGrpcSpanExporter.builder()
                            .setEndpoint(config.getEndpoint()),
                    OtlpGrpcSpanExporterBuilder::setTrustedCertificates,
                    OtlpGrpcSpanExporterBuilder::setClientTls)
                    .build();
            case HTTP_PROTOBUF -> configureTls(
                    config,
                    OtlpHttpSpanExporter.builder()
                            .setEndpoint(config.getEndpoint()),
                    OtlpHttpSpanExporterBuilder::setTrustedCertificates,
                    OtlpHttpSpanExporterBuilder::setClientTls)
                    .build();
        };
    }

    @ProvidesIntoSet
    public static MetricReader createMetricReader(OpenTelemetryExporterConfig config)
    {
        return PeriodicMetricReader.builder(createMetricExporter(config))
                .setInterval(config.getInterval().toJavaTime())
                .build();
    }

    static MetricExporter createMetricExporter(OpenTelemetryExporterConfig config)
    {
        return switch (config.getProtocol()) {
            case GRPC -> configureTls(
                    config,
                    OtlpGrpcMetricExporter.builder()
                            .setEndpoint(config.getEndpoint()),
                    OtlpGrpcMetricExporterBuilder::setTrustedCertificates,
                    OtlpGrpcMetricExporterBuilder::setClientTls)
                    .build();
            case HTTP_PROTOBUF -> configureTls(
                    config,
                    OtlpHttpMetricExporter.builder()
                            .setEndpoint(config.getEndpoint()),
                    OtlpHttpMetricExporterBuilder::setTrustedCertificates,
                    OtlpHttpMetricExporterBuilder::setClientTls)
                    .build();
        };
    }

    @ProvidesIntoSet
    public static LogRecordProcessor createLogRecordProcessor(OpenTelemetryExporterConfig config, SdkMeterProvider meterProvider)
    {
        BatchLogRecordProcessorBuilder builder = BatchLogRecordProcessor.builder(createLogRecordExporter(config))
                .setMeterProvider(meterProvider);
        config.getLogMaxExportBatchSize().ifPresent(builder::setMaxExportBatchSize);
        config.getLogMaxQueueSize().ifPresent(builder::setMaxQueueSize);
        config.getLogScheduleDelay().ifPresent(delay -> builder.setScheduleDelay(delay.toJavaTime()));
        builder.setInternalTelemetryVersion(InternalTelemetryVersion.LATEST);
        return builder.build();
    }

    static LogRecordExporter createLogRecordExporter(OpenTelemetryExporterConfig config)
    {
        return switch (config.getProtocol()) {
            case GRPC -> configureTls(
                    config,
                    OtlpGrpcLogRecordExporter.builder()
                            .setEndpoint(config.getEndpoint()),
                    OtlpGrpcLogRecordExporterBuilder::setTrustedCertificates,
                    OtlpGrpcLogRecordExporterBuilder::setClientTls)
                    .build();
            case HTTP_PROTOBUF -> configureTls(
                    config,
                    OtlpHttpLogRecordExporter.builder()
                            .setEndpoint(config.getEndpoint()),
                    OtlpHttpLogRecordExporterBuilder::setTrustedCertificates,
                    OtlpHttpLogRecordExporterBuilder::setClientTls)
                    .build();
        };
    }

    private static <T> T configureTls(
            OpenTelemetryExporterConfig config,
            T builder,
            BiConsumer<T, byte[]> trustedCertificatesSetter,
            ClientTlsSetter<T> clientTlsSetter)
    {
        config.getTrustedCertificatesPath()
                .map(path -> readFile(path, "OpenTelemetry trusted certificates"))
                .or(() -> config.getTrustedCertificatesPem().map(OpenTelemetryExporterModule::pemToBytes))
                .ifPresent(trustedCertificates -> trustedCertificatesSetter.accept(builder, trustedCertificates));

        Optional<byte[]> clientKey = readPrivateKey(config.getClientKeyPath(), config.getClientKeyPem(), config.getClientKeyPassword());
        Optional<byte[]> clientCertificate = config.getClientCertificatePath()
                .map(path -> readFile(path, "OpenTelemetry client certificate"))
                .or(() -> config.getClientCertificatePem().map(OpenTelemetryExporterModule::pemToBytes));
        if (clientKey.isPresent() && clientCertificate.isPresent()) {
            clientTlsSetter.accept(
                    builder,
                    clientKey.orElseThrow(),
                    clientCertificate.orElseThrow());
        }

        return builder;
    }

    private static byte[] readFile(Path path, String description)
    {
        try {
            return readAllBytes(path);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + description + " file: " + path, e);
        }
    }

    private static Optional<byte[]> readPrivateKey(Optional<Path> path, Optional<String> pem, Optional<String> password)
    {
        if (path.isEmpty() && pem.isEmpty()) {
            return Optional.empty();
        }

        if (password.isEmpty()) {
            return Optional.of(path.map(file -> readFile(file, "OpenTelemetry client key"))
                    .orElseGet(() -> pemToBytes(pem.orElseThrow())));
        }

        try {
            // OTLP does not accept encrypted PEM private keys, so decode and reencode the key.
            if (path.isPresent()) {
                return Optional.of(PemWriter.writePrivateKey(PemReader.loadPrivateKey(path.orElseThrow().toFile(), password)).getBytes(US_ASCII));
            }
            return Optional.of(PemWriter.writePrivateKey(PemReader.loadPrivateKey(pem.orElseThrow(), password)).getBytes(US_ASCII));
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to read OpenTelemetry client key", e);
        }
        catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Failed to read OpenTelemetry client key", e);
        }
    }

    private static byte[] pemToBytes(String pem)
    {
        return pem.getBytes(US_ASCII);
    }

    @FunctionalInterface
    private interface ClientTlsSetter<T>
    {
        void accept(T builder, byte[] privateKeyPem, byte[] certificatePem);
    }
}
