package io.airlift.opentelemetry;

import io.airlift.security.cert.CertificateBuilder;
import io.airlift.security.pem.PemWriter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.security.auth.x500.X500Principal;

import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.LocalDate;
import java.time.ZoneId;

import static io.airlift.opentelemetry.OpenTelemetryExporterConfig.Protocol.GRPC;
import static io.airlift.opentelemetry.OpenTelemetryExporterConfig.Protocol.HTTP_PROTOBUF;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Base64.getMimeEncoder;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.crypto.Cipher.ENCRYPT_MODE;
import static org.assertj.core.api.Assertions.assertThat;

final class TestOpenTelemetryExporterModule
{
    private static final String KEY_PASSWORD = "key-password";

    @Test
    void testHttpProtobufEndpointAddsSignalPath()
    {
        assertThat(OpenTelemetryExporterModule.httpProtobufEndpoint(URI.create("http://localhost:4318"), "v1/traces"))
                .isEqualTo("http://localhost:4318/v1/traces");
        assertThat(OpenTelemetryExporterModule.httpProtobufEndpoint(URI.create("http://localhost:4318/"), "v1/metrics"))
                .isEqualTo("http://localhost:4318/v1/metrics");
        assertThat(OpenTelemetryExporterModule.httpProtobufEndpoint(URI.create("http://localhost:4318/collector"), "v1/logs"))
                .isEqualTo("http://localhost:4318/collector/v1/logs");
        assertThat(OpenTelemetryExporterModule.httpProtobufEndpoint(URI.create("http://localhost:4318/collector/"), "v1/traces"))
                .isEqualTo("http://localhost:4318/collector/v1/traces");
    }

    @Test
    void testHttpProtobufEndpointReplacesExistingSignalPath()
    {
        assertThat(OpenTelemetryExporterModule.httpProtobufEndpoint(URI.create("http://localhost:4318/v1/traces"), "v1/traces"))
                .isEqualTo("http://localhost:4318/v1/traces");
        assertThat(OpenTelemetryExporterModule.httpProtobufEndpoint(URI.create("http://localhost:4318/v1/traces"), "v1/metrics"))
                .isEqualTo("http://localhost:4318/v1/metrics");
        assertThat(OpenTelemetryExporterModule.httpProtobufEndpoint(URI.create("http://localhost:4318/collector/v1/logs"), "v1/traces"))
                .isEqualTo("http://localhost:4318/collector/v1/traces");
        assertThat(OpenTelemetryExporterModule.httpProtobufEndpoint(URI.create("http://localhost:4318/collector/v1/metrics/"), "v1/logs"))
                .isEqualTo("http://localhost:4318/collector/v1/logs");
    }

    @Test
    void testGrpcExporterIsCreated()
    {
        OpenTelemetryExporterConfig config = new OpenTelemetryExporterConfig()
                .setProtocol(GRPC)
                .setEndpoint(URI.create("http://localhost:4317"));

        assertExportersCreated(config);
    }

    @Test
    void testHttpExporterIsCreated()
    {
        OpenTelemetryExporterConfig config = new OpenTelemetryExporterConfig()
                .setProtocol(HTTP_PROTOBUF)
                .setEndpoint(URI.create("http://localhost:4318"));

        assertExportersCreated(config);
    }

    @Test
    void testPemTlsConfigIsLoaded()
            throws Exception
    {
        TlsMaterials tlsMaterials = createTlsMaterials();

        assertExportersCreated(new OpenTelemetryExporterConfig()
                .setProtocol(GRPC)
                .setEndpoint(URI.create("https://localhost:4317"))
                .setTrustedCertificatesPem(tlsMaterials.certificatePem())
                .setClientCertificatePem(tlsMaterials.certificatePem())
                .setClientKeyPem(tlsMaterials.privateKeyPem()));

        assertExportersCreated(new OpenTelemetryExporterConfig()
                .setProtocol(HTTP_PROTOBUF)
                .setEndpoint(URI.create("https://localhost:4318"))
                .setTrustedCertificatesPem(tlsMaterials.certificatePem())
                .setClientCertificatePem(tlsMaterials.certificatePem())
                .setClientKeyPem(tlsMaterials.privateKeyPem()));
    }

    @Test
    void testEncryptedPemClientKeyIsLoaded()
            throws Exception
    {
        TlsMaterials tlsMaterials = createTlsMaterials();

        assertExportersCreated(new OpenTelemetryExporterConfig()
                .setProtocol(GRPC)
                .setEndpoint(URI.create("https://localhost:4317"))
                .setTrustedCertificatesPem(tlsMaterials.certificatePem())
                .setClientCertificatePem(tlsMaterials.certificatePem())
                .setClientKeyPem(tlsMaterials.encryptedPrivateKeyPem())
                .setClientKeyPassword(KEY_PASSWORD));

        assertExportersCreated(new OpenTelemetryExporterConfig()
                .setProtocol(HTTP_PROTOBUF)
                .setEndpoint(URI.create("https://localhost:4318"))
                .setTrustedCertificatesPem(tlsMaterials.certificatePem())
                .setClientCertificatePem(tlsMaterials.certificatePem())
                .setClientKeyPem(tlsMaterials.encryptedPrivateKeyPem())
                .setClientKeyPassword(KEY_PASSWORD));
    }

    private static void assertExportersCreated(OpenTelemetryExporterConfig config)
    {
        SpanExporter spanExporter = null;
        MetricExporter metricExporter = null;
        LogRecordExporter logRecordExporter = null;
        try {
            spanExporter = OpenTelemetryExporterModule.createSpanExporter(config);
            metricExporter = OpenTelemetryExporterModule.createMetricExporter(config);
            logRecordExporter = OpenTelemetryExporterModule.createLogRecordExporter(config);

            if (config.getProtocol() == GRPC) {
                assertThat(spanExporter).isInstanceOf(OtlpGrpcSpanExporter.class);
                assertThat(metricExporter).isInstanceOf(OtlpGrpcMetricExporter.class);
                assertThat(logRecordExporter).isInstanceOf(OtlpGrpcLogRecordExporter.class);
                return;
            }

            assertThat(spanExporter).isInstanceOf(OtlpHttpSpanExporter.class);
            assertThat(metricExporter).isInstanceOf(OtlpHttpMetricExporter.class);
            assertThat(logRecordExporter).isInstanceOf(OtlpHttpLogRecordExporter.class);
        }
        finally {
            if (spanExporter != null) {
                spanExporter.shutdown().join(10, SECONDS);
            }
            if (metricExporter != null) {
                metricExporter.shutdown().join(10, SECONDS);
            }
            if (logRecordExporter != null) {
                logRecordExporter.shutdown().join(10, SECONDS);
            }
        }
    }

    private static TlsMaterials createTlsMaterials()
            throws Exception
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        LocalDate notBefore = LocalDate.now(ZoneId.systemDefault());

        X509Certificate certificate = CertificateBuilder.certificateBuilder()
                .setKeyPair(keyPair)
                .setSerialNumber(12345)
                .setIssuer(new X500Principal("CN=issuer,O=Airlift"))
                .setNotBefore(notBefore)
                .setNotAfter(notBefore.plusDays(1))
                .setSubject(new X500Principal("CN=subject,O=Airlift"))
                .buildSelfSigned();

        return new TlsMaterials(
                PemWriter.writeCertificate(certificate),
                PemWriter.writePrivateKey(keyPair.getPrivate()),
                writeEncryptedPrivateKey(keyPair));
    }

    private static String writeEncryptedPrivateKey(KeyPair keyPair)
            throws Exception
    {
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
        SecretKey key = keyFactory.generateSecret(new PBEKeySpec(KEY_PASSWORD.toCharArray()));

        Cipher cipher = Cipher.getInstance("PBEWithMD5AndDES");
        cipher.init(ENCRYPT_MODE, key, new PBEParameterSpec("12345678".getBytes(US_ASCII), 1_000));

        EncryptedPrivateKeyInfo encryptedPrivateKeyInfo = new EncryptedPrivateKeyInfo(cipher.getParameters(), cipher.doFinal(keyPair.getPrivate().getEncoded()));
        return pem("ENCRYPTED PRIVATE KEY", encryptedPrivateKeyInfo.getEncoded());
    }

    private static String pem(String type, byte[] encoded)
    {
        return "-----BEGIN " + type + "-----\n" +
                getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded) + '\n' +
                "-----END " + type + "-----\n";
    }

    private record TlsMaterials(String certificatePem, String privateKeyPem, String encryptedPrivateKeyPem) {}
}
