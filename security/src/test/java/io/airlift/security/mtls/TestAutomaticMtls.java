package io.airlift.security.mtls;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.security.auth.x500.X500Principal;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import static com.google.common.io.BaseEncoding.base16;
import static io.airlift.security.mtls.AutomaticMtls.addCertificateAndKeyForCurrentNode;
import static io.airlift.security.mtls.AutomaticMtls.addCertificateToKeyStore;
import static io.airlift.security.mtls.AutomaticMtls.certificateBuilder;
import static io.airlift.security.mtls.AutomaticMtls.createSSLContext;
import static io.airlift.security.mtls.AutomaticMtls.inMemoryKeyStore;
import static java.net.http.HttpResponse.BodyHandlers.discarding;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAutomaticMtls
{
    private static final String KEYSTORE_PASSWORD = "123456";

    @Test
    public void testKeyDerivationIsStable()
            throws GeneralSecurityException
    {
        // Peers derive the same key pair independently from the shared secret, so the derivation
        // must produce identical keys across releases and JVMs. Locking in the derived public key
        // for a fixed secret catches any accidental change to the derivation, which would break
        // mTLS between nodes running different versions.
        X509Certificate certificate = certificateBuilder("test-shared-secret", "test-common-name").buildSelfSigned();
        assertThat(base16().lowerCase().encode(certificate.getPublicKey().getEncoded()))
                .isEqualTo("3059301306072a8648ce3d020106082a8648ce3d0301070342000499bffd6cd783605d4766fb8ac4443d5efad60dc0af213b06d955f7e32c0cc92d8a058b1e3c923220da040419c5b4f98737e1bf1185d02b5da0168adc90e035bb");
    }

    @Test
    public void testTrustManager()
            throws GeneralSecurityException
    {
        X509Certificate certificate = certificateBuilder("sharedSecret", "commonName")
                .addSanDnsNames(List.of("localhost"))
                .buildSelfSigned();

        // This should pass as the certificate is self-signed and matches the trust manager's expectations
        checkCertificate(certificate, "sharedSecret", "commonName");

        assertThatThrownBy(() -> checkCertificate(certificate, "wrongSharedSecret", "commonName"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Peer cert public key doesn't match trusted key");

        // the common name is part of the key derivation, so a different common name fails the key check
        assertThatThrownBy(() -> checkCertificate(certificate, "sharedSecret", "wrongCommonName"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Peer cert public key doesn't match trusted key");

        X509Certificate wrongSubjectCertificate = certificateBuilder("sharedSecret", "commonName")
                .setSubject(new X500Principal("CN=wrongCommonName"))
                .buildSelfSigned();

        assertThatThrownBy(() -> checkCertificate(wrongSubjectCertificate, "sharedSecret", "commonName"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Peer certificate subject 'CN=wrongCommonName' does not match expected subject: 'CN=commonName'");
    }

    @Test
    public void testMutualAuthentication()
            throws Exception
    {
        KeyStore serverKeyStore = inMemoryKeyStore();
        X509Certificate serverCert = addCertificateAndKeyForCurrentNode("sharedSecret", "commonName", serverKeyStore, KEYSTORE_PASSWORD);
        SSLContext serverSSLContext = createSSLContext("sharedSecret", "commonName", serverKeyStore, KEYSTORE_PASSWORD);

        KeyStore clientKeyStore = inMemoryKeyStore();
        X509Certificate clientCert = certificateBuilder("sharedSecret", "commonName")
                .addSanDnsNames(List.of("client-address"))
                .buildSelfSigned();

        addCertificateToKeyStore("sharedSecret", "commonName", clientCert, clientKeyStore, KEYSTORE_PASSWORD);
        SSLContext clientSSLContext = createSSLContext("sharedSecret", "commonName", clientKeyStore, KEYSTORE_PASSWORD);

        assertThat(serverCert)
                .isNotEqualTo(clientCert);

        HttpsServer server = HttpsServer.create(new InetSocketAddress(0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverSSLContext)
        {
            @Override
            public void configure(HttpsParameters params)
            {
                SSLParameters parameters = serverSSLContext.getDefaultSSLParameters();
                parameters.setNeedClientAuth(true);
                params.setSSLParameters(parameters);
            }
        });

        server.createContext("/hello", new HelloWorldHandler());
        server.setExecutor(null);
        server.start();

        int serverPort = server.getAddress().getPort();

        HttpRequest request = HttpRequest
                .newBuilder(new URI("https://127.0.0.1:%d/hello".formatted(serverPort)))
                .GET()
                .build();

        try (HttpClient plainClient = HttpClient.newBuilder().build()) {
            assertThatThrownBy(() -> plainClient.send(request, discarding()).statusCode())
                    .isInstanceOf(SSLHandshakeException.class)
                    .hasMessageContaining("unable to find valid certification path to requested target");
        }

        try (HttpClient authenticatedClient = HttpClient.newBuilder().sslContext(clientSSLContext).build()) {
            // Will pass
            HttpResponse<String> response = authenticatedClient.send(request, ofString());
            assertThat(response.statusCode())
                    .isEqualTo(200);
            assertThat(response.body())
                    .isEqualTo("Hello, client serial: " + clientCert.getSerialNumber() + ", server serial " + serverCert.getSerialNumber() + ", san dns: [[2, client-address]]");

            // Will fail as the certificate is not trusted
            assertThatThrownBy(() -> authenticatedClient.send(HttpRequest.newBuilder(new URI("https://localhost:%d/hello".formatted(serverPort))).GET().build(), discarding()))
                    .isInstanceOf(SSLHandshakeException.class)
                    .hasMessageContaining("(certificate_unknown) No subject alternative DNS name matching localhost found");
        }
    }

    private void checkCertificate(X509Certificate certificate, String sharedSecret, String commonName)
            throws CertificateException
    {
        AutomaticMtls.createTrustManager(sharedSecret, commonName).checkClientTrusted(new X509Certificate[] {certificate}, "ECDSA");
    }

    private static class HelloWorldHandler
            implements HttpHandler
    {
        @Override
        public void handle(HttpExchange exchange)
                throws IOException
        {
            try {
                if (exchange instanceof HttpsExchange httpsExchange) {
                    X509Certificate peerCertificates = (X509Certificate) httpsExchange.getSSLSession().getPeerCertificates()[0];
                    X509Certificate localCertificates = (X509Certificate) httpsExchange.getSSLSession().getLocalCertificates()[0];
                    String response = "Hello, client serial: " + peerCertificates.getSerialNumber() + ", server serial " + localCertificates.getSerialNumber() + ", san dns: " + peerCertificates.getSubjectAlternativeNames();
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes(UTF_8));
                    exchange.close();
                    return;
                }
                exchange.sendResponseHeaders(400, 0);
                exchange.getResponseBody().write("Not in SSL".getBytes(UTF_8));
                exchange.close();
            }
            catch (Exception e) {
                exchange.sendResponseHeaders(500, 0);
                exchange.getResponseBody().write(("Error: " + e.getMessage()).getBytes(UTF_8));
                exchange.close();
            }
        }
    }
}
