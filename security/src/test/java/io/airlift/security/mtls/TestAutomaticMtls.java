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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.List;

import static io.airlift.security.mtls.AutomaticMtls.addCertificateAndKeyForCurrentNode;
import static io.airlift.security.mtls.AutomaticMtls.addNodeCertificateAndKey;
import static io.airlift.security.mtls.AutomaticMtls.caCertificate;
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
    private static final String SHARED_SECRET = "shared-secret-value-with-enough-entropy-0123456789";
    private static final String WRONG_SHARED_SECRET = "wrong-secret-value-with-enough-entropy-0123456789";
    private static final String ENVIRONMENT = "commonName";

    @Test
    public void testCaDerivationIsDeterministicAndBoundToEnvironment()
            throws Exception
    {
        // Same secret + same environment must always derive the same CA public key: independently
        // started nodes derive the same CA and therefore trust each other. This is also a "known
        // answer" vector that fails if the derivation algorithm silently changes.
        String expectedCaPublicKey = "3059301306072a8648ce3d020106082a8648ce3d030107034200045ce3bf4163eb83e851347e4aa756ed06a992572ee8690a1823d5054217938ba899694b345eb88a4d244748569395fd37de6967534c91fe4f8b4076d344c88a26";
        assertThat(caPublicKeyHex(SHARED_SECRET, "test-environment"))
                .isEqualTo(caPublicKeyHex(SHARED_SECRET, "test-environment"))
                .isEqualTo(expectedCaPublicKey);

        // A different environment (used as the KDF salt) must derive a different CA.
        assertThat(caPublicKeyHex(SHARED_SECRET, "other-environment"))
                .isNotEqualTo(expectedCaPublicKey);
    }

    @Test
    public void testShortSharedSecretIsRejected()
    {
        assertThatThrownBy(() -> caCertificate("too-short", ENVIRONMENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("automatic HTTPS shared secret must be at least 32 characters");
    }

    @Test
    public void testLeafIsSignedByDerivedCa()
            throws Exception
    {
        KeyStore keyStore = inMemoryKeyStore();
        X509Certificate leaf = addCertificateAndKeyForCurrentNode(SHARED_SECRET, ENVIRONMENT, keyStore, KEYSTORE_PASSWORD);

        // The leaf is issued by the CA derived from the same secret...
        leaf.verify(caCertificate(SHARED_SECRET, ENVIRONMENT).getPublicKey());

        // ...but not by a CA derived from a different secret.
        assertThatThrownBy(() -> leaf.verify(caCertificate(WRONG_SHARED_SECRET, ENVIRONMENT).getPublicKey()))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    public void testMutualAuthentication()
            throws Exception
    {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");

        KeyStore serverKeyStore = inMemoryKeyStore();
        X509Certificate serverCert = addNodeCertificateAndKey(SHARED_SECRET, ENVIRONMENT, serverKeyStore, KEYSTORE_PASSWORD, List.of(loopback), List.of());
        SSLContext serverSSLContext = createSSLContext(SHARED_SECRET, ENVIRONMENT, serverKeyStore, KEYSTORE_PASSWORD);

        KeyStore clientKeyStore = inMemoryKeyStore();
        X509Certificate clientCert = addNodeCertificateAndKey(SHARED_SECRET, ENVIRONMENT, clientKeyStore, KEYSTORE_PASSWORD, List.of(), List.of("client-address"));
        SSLContext clientSSLContext = createSSLContext(SHARED_SECRET, ENVIRONMENT, clientKeyStore, KEYSTORE_PASSWORD);

        // Every node has its own randomly generated leaf key pair.
        assertThat(serverCert).isNotEqualTo(clientCert);
        assertThat(serverCert.getPublicKey()).isNotEqualTo(clientCert.getPublicKey());

        HttpsServer server = HttpsServer.create(new InetSocketAddress(loopback, 0), 0);
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
            HttpResponse<String> response = authenticatedClient.send(request, ofString());
            assertThat(response.statusCode())
                    .isEqualTo(200);
            assertThat(response.body())
                    .isEqualTo("Hello, client serial: " + clientCert.getSerialNumber() + ", server serial " + serverCert.getSerialNumber() + ", san dns: [[2, client-address]]");

            // Fails hostname verification: the server leaf has no SAN matching localhost.
            assertThatThrownBy(() -> authenticatedClient.send(HttpRequest.newBuilder(new URI("https://localhost:%d/hello".formatted(serverPort))).GET().build(), discarding()))
                    .isInstanceOf(SSLHandshakeException.class)
                    .hasMessageContaining("No name matching localhost found");
        }
    }

    private static String caPublicKeyHex(String sharedSecret, String environment)
            throws Exception
    {
        return HexFormat.of().formatHex(caCertificate(sharedSecret, environment).getPublicKey().getEncoded());
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
