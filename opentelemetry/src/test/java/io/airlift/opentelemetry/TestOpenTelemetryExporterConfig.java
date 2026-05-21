package io.airlift.opentelemetry;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.validation.FileExists;
import io.airlift.units.Duration;
import jakarta.validation.constraints.AssertTrue;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;
import static io.airlift.opentelemetry.OpenTelemetryExporterConfig.Protocol.GRPC;
import static io.airlift.opentelemetry.OpenTelemetryExporterConfig.Protocol.HTTP_PROTOBUF;
import static io.airlift.testing.ValidationAssertions.assertFailsValidation;

public class TestOpenTelemetryExporterConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(OpenTelemetryExporterConfig.class)
                .setEndpoint("http://localhost:4317")
                .setProtocol(GRPC)
                .setInterval(new Duration(1, TimeUnit.MINUTES))
                .setSpanMaxExportBatchSize(null)
                .setSpanMaxQueueSize(null)
                .setSpanScheduleDelay(null)
                .setLogMaxExportBatchSize(null)
                .setLogMaxQueueSize(null)
                .setLogScheduleDelay(null)
                .setTrustedCertificatesPath(null)
                .setClientCertificatePath(null)
                .setClientKeyPath(null));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("otel.exporter.endpoint", "http://example.com:1234")
                .put("otel.exporter.protocol", "http/protobuf")
                .put("otel.exporter.interval", "5m")
                .put("otel.exporter.span.max-export-batch-size", "128")
                .put("otel.exporter.span.max-queue-size", "4096")
                .put("otel.exporter.span.schedule-delay", "2s")
                .put("otel.exporter.log.max-export-batch-size", "64")
                .put("otel.exporter.log.max-queue-size", "1024")
                .put("otel.exporter.log.schedule-delay", "5s")
                .put("otel.exporter.tls.trusted-certificates-path", "./pom.xml")
                .put("otel.exporter.tls.client-certificate-path", "./pom.xml")
                .put("otel.exporter.tls.client-key-path", "./pom.xml")
                .buildOrThrow();

        OpenTelemetryExporterConfig expected = new OpenTelemetryExporterConfig()
                .setEndpoint("http://example.com:1234")
                .setProtocol(HTTP_PROTOBUF)
                .setInterval(new Duration(5, TimeUnit.MINUTES))
                .setSpanMaxExportBatchSize(128)
                .setSpanMaxQueueSize(4096)
                .setSpanScheduleDelay(new Duration(2, TimeUnit.SECONDS))
                .setLogMaxExportBatchSize(64)
                .setLogMaxQueueSize(1024)
                .setLogScheduleDelay(new Duration(5, TimeUnit.SECONDS))
                .setTrustedCertificatesPath(Path.of("./pom.xml"))
                .setClientCertificatePath(Path.of("./pom.xml"))
                .setClientKeyPath(Path.of("./pom.xml"));

        assertFullMapping(properties, expected);
    }

    @Test
    public void testClientCertificateWithoutKeyFailsValidation()
    {
        assertFailsValidation(
                new OpenTelemetryExporterConfig()
                        .setClientCertificatePath(Path.of("/certs/client.pem")),
                "clientTlsValid",
                "client certificate and key paths must be set together",
                AssertTrue.class);
    }

    @Test
    public void testClientKeyWithoutCertificateFailsValidation()
    {
        assertFailsValidation(
                new OpenTelemetryExporterConfig()
                        .setClientKeyPath(Path.of("/certs/client.key")),
                "clientTlsValid",
                "client certificate and key paths must be set together",
                AssertTrue.class);
    }

    @Test
    public void testTrustedCertificatesPathMustExist()
    {
        assertFailsValidation(
                new OpenTelemetryExporterConfig()
                        .setTrustedCertificatesPath(Path.of("./not-existing-trusted.pem")),
                "trustedCertificatesPath",
                "file does not exist: ./not-existing-trusted.pem",
                FileExists.class);
    }

    @Test
    public void testClientCertificatePathMustExist()
    {
        assertFailsValidation(
                new OpenTelemetryExporterConfig()
                        .setClientCertificatePath(Path.of("./not-existing-client.pem"))
                        .setClientKeyPath(Path.of("./pom.xml")),
                "clientCertificatePath",
                "file does not exist: ./not-existing-client.pem",
                FileExists.class);
    }

    @Test
    public void testClientKeyPathMustExist()
    {
        assertFailsValidation(
                new OpenTelemetryExporterConfig()
                        .setClientCertificatePath(Path.of("./pom.xml"))
                        .setClientKeyPath(Path.of("./not-existing-client.key")),
                "clientKeyPath",
                "file does not exist: ./not-existing-client.key",
                FileExists.class);
    }
}
