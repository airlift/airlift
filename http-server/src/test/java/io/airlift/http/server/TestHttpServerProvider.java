/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.http.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HostAndPort;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpClient.HttpResponseFuture;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpUriBuilder;
import io.airlift.http.client.Request;
import io.airlift.http.client.Request.Builder;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.HttpServer.ClientCertificate;
import io.airlift.log.Logging;
import io.airlift.node.NodeConfig;
import io.airlift.node.NodeInfo;
import io.airlift.testing.TempFile;
import io.airlift.units.Duration;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;

import javax.security.auth.x500.X500Principal;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static com.google.common.io.Resources.getResource;
import static com.google.common.net.HttpHeaders.X_FORWARDED_FOR;
import static com.google.common.net.HttpHeaders.X_FORWARDED_HOST;
import static com.google.common.net.HttpHeaders.X_FORWARDED_PROTO;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.http.server.HttpServerConfig.ProcessForwardedMode.ACCEPT;
import static io.airlift.http.server.HttpServerConfig.ProcessForwardedMode.IGNORE;
import static io.airlift.http.server.HttpServerConfig.ProcessForwardedMode.REJECT;
import static io.airlift.http.server.TestHttpServerInfo.closeChannels;
import static io.airlift.testing.Closeables.closeAll;
import static java.lang.String.format;
import static java.nio.file.Files.createTempDirectory;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
public class TestHttpServerProvider
{
    private HttpServer server;
    private File tempDir;
    private NodeInfo nodeInfo;
    private HttpServerConfig config;
    private HttpsConfig httpsConfig;
    private ClientCertificate clientCertificate;
    private HttpServerInfo httpServerInfo;

    @BeforeAll
    public void setupSuite()
    {
        Logging.initialize();
    }

    @BeforeEach
    public void setup()
            throws IOException
    {
        tempDir = createTempDirectory(getClass().getSimpleName()).toFile();
        config = new HttpServerConfig()
                .setHttpPort(0)
                .setLogPath(new File(tempDir, "http-request.log").getAbsolutePath());
        httpsConfig = new HttpsConfig()
                .setHttpsPort(0);
        clientCertificate = ClientCertificate.NONE;
        nodeInfo = new NodeInfo(new NodeConfig()
                .setEnvironment("test")
                .setNodeInternalAddress("localhost"));
        httpServerInfo = createHttpServerInfo();
    }

    @AfterEach
    public void teardown()
            throws Exception
    {
        closeAll((server != null) ? (AutoCloseable) server::stop : null,
                () -> closeChannels(httpServerInfo),
                () -> deleteRecursively(tempDir.toPath(), ALLOW_INSECURE));
    }

    @Test
    public void testConnectorDefaults()
    {
        assertThat(config.isHttpEnabled()).isTrue();
        assertThat(httpServerInfo.getHttpUri()).isNotNull();
        assertThat(httpServerInfo.getHttpExternalUri()).isNotNull();
        assertThat(httpServerInfo.getHttpChannel()).isNotNull();
        assertThat(httpServerInfo.getHttpUri().getScheme()).isEqualTo(httpServerInfo.getHttpExternalUri().getScheme());
        assertThat(httpServerInfo.getHttpUri().getPort()).isEqualTo(httpServerInfo.getHttpExternalUri().getPort());
        assertThat(httpServerInfo.getHttpUri().getScheme()).isEqualTo("http");

        assertThat(config.isHttpsEnabled()).isFalse();
        assertThat(httpServerInfo.getHttpsUri()).isNull();
        assertThat(httpServerInfo.getHttpsExternalUri()).isNull();
        assertThat(httpServerInfo.getHttpsChannel()).isNull();
    }

    @Test
    public void testHttpDisabled()
    {
        config.setHttpEnabled(false);
        httpServerInfo = createHttpServerInfo();

        assertThat(httpServerInfo.getHttpUri()).isNull();
        assertThat(httpServerInfo.getHttpExternalUri()).isNull();
        assertThat(httpServerInfo.getHttpChannel()).isNull();

        assertThat(httpServerInfo.getHttpsUri()).isNull();
        assertThat(httpServerInfo.getHttpsExternalUri()).isNull();
        assertThat(httpServerInfo.getHttpsChannel()).isNull();
    }

    @Test
    public void testHttpsEnabled()
    {
        config.setHttpsEnabled(true);
        httpServerInfo = createHttpServerInfo();

        assertThat(httpServerInfo.getHttpUri()).isNotNull();
        assertThat(httpServerInfo.getHttpExternalUri()).isNotNull();
        assertThat(httpServerInfo.getHttpChannel()).isNotNull();
        assertThat(httpServerInfo.getHttpUri().getScheme()).isEqualTo(httpServerInfo.getHttpExternalUri().getScheme());
        assertThat(httpServerInfo.getHttpUri().getPort()).isEqualTo(httpServerInfo.getHttpExternalUri().getPort());
        assertThat(httpServerInfo.getHttpUri().getScheme()).isEqualTo("http");

        assertThat(httpServerInfo.getHttpsUri()).isNotNull();
        assertThat(httpServerInfo.getHttpsExternalUri()).isNotNull();
        assertThat(httpServerInfo.getHttpsChannel()).isNotNull();
        assertThat(httpServerInfo.getHttpsUri().getScheme()).isEqualTo(httpServerInfo.getHttpsExternalUri().getScheme());
        assertThat(httpServerInfo.getHttpsUri().getPort()).isEqualTo(httpServerInfo.getHttpsExternalUri().getPort());
        assertThat(httpServerInfo.getHttpsUri().getScheme()).isEqualTo("https");

        assertThat(httpServerInfo.getHttpUri().getPort()).isNotEqualTo(httpServerInfo.getHttpsUri().getPort());
    }

    @Test
    public void testHttp()
            throws Exception
    {
        createServer();
        server.start();

        try (JettyHttpClient httpClient = new JettyHttpClient(new HttpClientConfig().setHttp2Enabled(false))) {
            StatusResponse response = httpClient.execute(prepareGet().setUri(httpServerInfo.getHttpUri()).build(), createStatusResponseHandler());

            assertThat(response.getStatusCode()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getHeader("X-Protocol")).isEqualTo("HTTP/1.1");
        }

        try (JettyHttpClient httpClient = new JettyHttpClient(new HttpClientConfig().setHttp2Enabled(true))) {
            StatusResponse response = httpClient.execute(prepareGet().setUri(httpServerInfo.getHttpUri()).build(), createStatusResponseHandler());

            assertThat(response.getStatusCode()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getHeader("X-Protocol")).isEqualTo("HTTP/2.0");
        }
    }

    @Test
    public void testHttps()
            throws Exception
    {
        config.setHttpEnabled(false)
                .setHttpsEnabled(true);
        httpsConfig
                .setKeystorePath(getResource("test.keystore.with.two.passwords").getPath())
                .setKeystorePassword("airlift")
                .setKeyManagerPassword("airliftkey")
                .setAutomaticHttpsSharedSecret("shared-secret");

        createAndStartServer();

        HttpClientConfig http1ClientConfig = new HttpClientConfig()
                .setHttp2Enabled(false)
                .setTrustStorePath(getResource("test.truststore").getPath())
                .setTrustStorePassword("airlift")
                .setAutomaticHttpsSharedSecret("shared-secret");

        try (JettyHttpClient httpClient = createJettyClient(http1ClientConfig)) {
            verifyHttps(httpClient, "localhost");
            verifyHttps(httpClient, "127-0-0-1.ip");
            verifyHttps(httpClient, "x--1.ip");
        }
    }

    private void verifyHttps(JettyHttpClient httpClient, String name)
    {
        URI uri = URI.create(format("https://%s:%s", name, httpServerInfo.getHttpsUri().getPort()));
        StatusResponse response = httpClient.execute(prepareGet().setUri(uri).build(), createStatusResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getHeader("X-Protocol")).isEqualTo("HTTP/1.1");
    }

    @Test
    public void testFilter()
            throws Exception
    {
        createServer();
        server.start();

        try (JettyHttpClient client = new JettyHttpClient()) {
            StatusResponse response = client.execute(prepareGet().setUri(httpServerInfo.getHttpUri().resolve("/filter")).build(), createStatusResponseHandler());

            assertThat(response.getStatusCode()).isEqualTo(HttpServletResponse.SC_PAYMENT_REQUIRED);
        }
    }

    @Test
    public void testForwardedAccepted()
            throws Exception
    {
        config.setProcessForwarded(ACCEPT);
        ForwardedServlet servlet = new ForwardedServlet();
        createServer(servlet);
        server.start();

        assertForward(servlet, Optional.of("unknown"), Optional.empty(), Optional.empty());
        assertForward(servlet, Optional.of("https"), Optional.empty(), Optional.empty());

        assertForward(servlet, Optional.empty(), Optional.of("example.com:1234"), Optional.empty());

        assertForward(servlet, Optional.empty(), Optional.empty(), Optional.of("remote.example.com"));

        assertForward(servlet, Optional.of("unknown"), Optional.of("example.com:1234"), Optional.of("remote.example.com"));
        assertForward(servlet, Optional.of("https"), Optional.of("example.com:1234"), Optional.of("remote.example.com"));
    }

    @Test
    public void testForwardedRejecting()
            throws Exception
    {
        config.setProcessForwarded(REJECT);
        ForwardedServlet servlet = new ForwardedServlet();
        createServer(servlet);
        server.start();

        HttpUriBuilder uriBuilder = HttpUriBuilder.uriBuilderFrom(httpServerInfo.getHttpUri()).replacePath("/some/path");
        try (HttpClient client = new JettyHttpClient()) {
            Builder builder = prepareGet()
                    .setHeader(X_FORWARDED_PROTO, "https")
                    .setUri(uriBuilder.build());
            StringResponse response = client.execute(builder.build(), createStringResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(406);
        }
    }

    @Test
    public void testForwardedDropped()
            throws Exception
    {
        config.setProcessForwarded(IGNORE);
        ForwardedServlet servlet = new ForwardedServlet();
        createServer(servlet);
        server.start();

        HttpUriBuilder uriBuilder = HttpUriBuilder.uriBuilderFrom(httpServerInfo.getHttpUri()).replacePath("/some/path");
        try (HttpClient client = new JettyHttpClient()) {
            Builder builder = prepareGet()
                    .setHeader(X_FORWARDED_PROTO, "https")
                    .setUri(uriBuilder.build());
            StringResponse response = client.execute(builder.build(), createStringResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(200);
        }

        assertThat(servlet.getIsSecure()).isEqualTo(false);
    }

    private void assertForward(ForwardedServlet servlet, Optional<String> proto, Optional<String> host, Optional<String> remoteHost)
    {
        servlet.reset();

        HttpUriBuilder uriBuilder = HttpUriBuilder.uriBuilderFrom(httpServerInfo.getHttpUri()).replacePath("/some/path");
        try (HttpClient client = new JettyHttpClient()) {
            Builder builder = prepareGet()
                    .setUri(uriBuilder.build());
            proto.ifPresent(value -> builder.addHeader(X_FORWARDED_PROTO, value));
            host.ifPresent(value -> builder.addHeader(X_FORWARDED_HOST, value));
            remoteHost.ifPresent(value -> builder.addHeader(X_FORWARDED_FOR, value));
            StringResponse response = client.execute(builder.build(), createStringResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(200);
        }

        proto.ifPresent(uriBuilder::scheme);
        host.map(HostAndPort::fromString).ifPresent(uriBuilder::hostAndPort);
        URI forwardedUri = uriBuilder.build();
        assertThat(servlet.getRequestUrl()).isEqualTo(forwardedUri.toString());
        assertThat(servlet.getScheme()).isEqualTo(forwardedUri.getScheme());
        assertThat(servlet.getIsSecure()).isEqualTo((Boolean) forwardedUri.getScheme().equals("https"));

        remoteHost.ifPresent(value -> assertThat(servlet.getRemoteAddress()).isEqualTo(value));
    }

    @Test
    public void testClientCertificateJava()
            throws Exception
    {
        config.setHttpEnabled(false)
                .setHttpsEnabled(true);
        httpsConfig.setKeystorePath(getResource("clientcert-java/server.keystore").getPath())
                .setKeystorePassword("airlift")
                .setAutomaticHttpsSharedSecret("shared-secret");
        clientCertificate = ClientCertificate.REQUIRED;

        createAndStartServer(createCertTestServlet());

        HttpClientConfig clientConfig = new HttpClientConfig()
                .setKeyStorePath(getResource("clientcert-java/client.keystore").getPath())
                .setKeyStorePassword("airlift")
                .setTrustStorePath(getResource("clientcert-java/client.truststore").getPath())
                .setTrustStorePassword("airlift")
                .setAutomaticHttpsSharedSecret("shared-secret");

        assertClientCertificateRequest(clientConfig, "localhost");
        assertClientCertificateRequest(clientConfig, "127-0-0-1.ip");
        assertClientCertificateRequest(clientConfig, "x--1.ip");
    }

    @Test
    public void testClientCertificatePem()
            throws Exception
    {
        config.setHttpEnabled(false)
                .setHttpsEnabled(true);
        httpsConfig
                .setKeystorePath(getResource("clientcert-pem/server.pem").getPath())
                .setKeystorePassword("airlift")
                .setTrustStorePath(getResource("clientcert-pem/ca.crt").getPath());
        clientCertificate = ClientCertificate.REQUIRED;

        createAndStartServer(createCertTestServlet());

        HttpClientConfig clientConfig = new HttpClientConfig()
                .setKeyStorePath(getResource("clientcert-pem/client.pem").getPath())
                .setKeyStorePassword("airlift")
                .setTrustStorePath(getResource("clientcert-pem/ca.crt").getPath());

        assertClientCertificateRequest(clientConfig, "localhost");
    }

    private void assertClientCertificateRequest(HttpClientConfig clientConfig, String name)
    {
        try (JettyHttpClient httpClient = createJettyClient(clientConfig)) {
            URI uri = URI.create(format("https://%s:%s", name, httpServerInfo.getHttpsUri().getPort()));
            StringResponse response = httpClient.execute(prepareGet().setUri(uri).build(), createStringResponseHandler());

            assertThat(response.getStatusCode()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getBody()).isEqualTo("CN=testing,OU=Client,O=Airlift,L=Palo Alto,ST=CA,C=US");
        }
    }

    private static HttpServlet createCertTestServlet()
    {
        return new HttpServlet()
        {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response)
                    throws IOException
            {
                X509Certificate[] certs = (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");
                if ((certs == null) || (certs.length == 0)) {
                    throw new RuntimeException("No client certificate");
                }
                if (certs.length > 1) {
                    throw new RuntimeException("Received multiple client certificates");
                }
                X509Certificate cert = certs[0];
                response.getWriter().write(cert.getSubjectX500Principal().getName());
                response.setStatus(HttpServletResponse.SC_OK);
            }
        };
    }

    @Test
    public void testShowStackTraceEnabled()
            throws Exception
    {
        createServer(new ErrorServlet());
        server.start();

        try (HttpClient client = new JettyHttpClient()) {
            StringResponse response = client.execute(prepareGet().setUri(httpServerInfo.getHttpUri()).build(), createStringResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(500);
            assertThat(response.getBody()).contains("ErrorServlet.java");
        }
    }

    @Test
    public void testShowStackTraceDisabled()
            throws Exception
    {
        config.setShowStackTrace(false);
        createServer(new ErrorServlet());
        server.start();

        try (HttpClient client = new JettyHttpClient()) {
            StringResponse response = client.execute(prepareGet().setUri(httpServerInfo.getHttpUri()).build(), createStringResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(500);
            assertThat(response.getBody()).doesNotContain("ErrorServlet.java");
        }
    }

    @Test
    @Timeout(30)
    public void testStop()
            throws Exception
    {
        DummyServlet servlet = new DummyServlet();
        createAndStartServer(servlet);

        try (HttpClient client = new JettyHttpClient()) {
            URI uri = URI.create(httpServerInfo.getHttpUri().toASCIIString() + "/?sleep=50000");
            Request request = prepareGet().setUri(uri).build();
            HttpResponseFuture<?> future = client.executeAsync(request, createStatusResponseHandler());

            // wait until the servlet starts processing the request
            servlet.getSleeping().get(1, SECONDS);

            // stop server while the request is still active
            server.stop();

            // wait until the server is stopped
            server.join();

            // request should fail rather than sleeping the full duration
            assertThatThrownBy(() -> future.get(5, SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(UncheckedIOException.class)
                    .hasMessageContaining("Failed communicating with server");
        }
    }

    @Test
    public void testInsufficientThreadsHttp()
    {
        config.setMaxThreads(1);

        assertThatThrownBy(this::createAndStartServer)
                .hasMessageStartingWith("Insufficient configured threads: ");
    }

    @Test
    public void testInsufficientThreadsHttps()
    {
        config.setHttpEnabled(false)
                .setHttpsEnabled(true)
                .setMaxThreads(1);
        httpsConfig
                .setKeystorePath(getResource("test.keystore").getPath())
                .setKeystorePassword("airlift");

        assertThatThrownBy(this::createAndStartServer)
                .hasMessageStartingWith("Insufficient configured threads: ");
    }

    @Test
    public void testInsufficientPasswordToAccessKeystore()
    {
        config.setHttpEnabled(false)
                .setHttpsEnabled(true);
        httpsConfig
                .setKeystorePath(getResource("test.keystore.with.two.passwords").getPath())
                .setKeystorePassword("airlift");

        assertThatThrownBy(this::createAndStartServer)
                .hasRootCauseInstanceOf(UnrecoverableKeyException.class)
                .hasRootCauseMessage("Cannot recover key");
    }

    @Test
    public void testHttpsDaysUntilCertificateExpiration()
            throws Exception
    {
        config.setHttpEnabled(false)
                .setHttpsEnabled(true);
        httpsConfig
                .setKeystorePath(new File(getResource("test.keystore").toURI()).getAbsolutePath())
                .setKeystorePassword("airlift");

        createAndStartServer();

        // todo this should be positive but the certificate is expired
        assertThat(server.getDaysUntilCertificateExpiration()).isLessThan(0);
    }

    @Test
    public void testNoHttpsDaysUntilCertificateExpiration()
            throws Exception
    {
        config.setHttpEnabled(true);

        createAndStartServer();

        assertThat(server.getDaysUntilCertificateExpiration()).isNull();
    }

    @Test
    public void testKeystoreReloading()
            throws Exception
    {
        try (TempFile tempFile = new TempFile()) {
            appendCertificate(tempFile.file(), "certificate-1");
            config.setHttpsEnabled(true)
                    .setHttpEnabled(false);
            httpsConfig
                    .setSslContextRefreshTime(new Duration(5, SECONDS))
                    .setKeystorePath(tempFile.file().getAbsolutePath())
                    .setKeystorePassword("airlift");
            createAndStartServer();
            assertEventually(() -> assertThat(server.getCertificates()).hasSize(1));
            appendCertificate(tempFile.file(), "certificate-2");
            assertEventually(() -> assertThat(server.getCertificates()).hasSize(2));
        }
    }

    private JettyHttpClient createJettyClient(HttpClientConfig config)
    {
        return new JettyHttpClient("test", config, ImmutableList.of(), Optional.of(nodeInfo.getEnvironment()), Optional.empty());
    }

    private void createAndStartServer()
            throws Exception
    {
        createAndStartServer(new DummyServlet());
    }

    private void createAndStartServer(HttpServlet servlet)
            throws Exception
    {
        httpServerInfo = createHttpServerInfo();
        createServer(servlet);
        server.start();
    }

    private void createServer()
    {
        createServer(new DummyServlet());
    }

    private void createServer(HttpServlet servlet)
    {
        server = new HttpServerProvider(
                httpServerInfo,
                nodeInfo,
                config,
                optionalHttpsConfig(),
                servlet,
                ImmutableSet.of(new DummyFilter()),
                ImmutableSet.of(),
                false,
                false,
                false,
                clientCertificate,
                Optional.empty()).get();
    }

    private HttpServerInfo createHttpServerInfo()
    {
        return new HttpServerInfo(config, optionalHttpsConfig(), nodeInfo);
    }

    private Optional<HttpsConfig> optionalHttpsConfig()
    {
        return config.isHttpsEnabled() ? Optional.of(this.httpsConfig) : Optional.empty();
    }

    private static void appendCertificate(File keyStoreFile, String alias)
            throws Exception
    {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        char[] password = "airlift".toCharArray();
        try (InputStream inStream = new FileInputStream(keyStoreFile)) {
            keyStore.load(inStream, password);
        }
        catch (EOFException ignored) { // reading an empty file produces EOFException
            keyStore.load(null, password);
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();

        X500Principal issuer = new X500Principal("CN=Airlift Test, OU=Airlift, O=Airlift, L=Palo Alto, ST=CA, C=US");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer,
                BigInteger.valueOf(System.currentTimeMillis()),
                Date.from(Instant.now()),
                Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
                issuer,
                keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(privateKey);
        X509CertificateHolder certHolder = builder.build(signer);
        Certificate cert = new JcaX509CertificateConverter().getCertificate(certHolder);

        keyStore.setKeyEntry(alias, privateKey, password, new Certificate[] {cert});
        try (OutputStream outStream = new FileOutputStream(keyStoreFile)) {
            keyStore.store(outStream, password);
        }
    }

    private static void assertEventually(Runnable assertion)
    {
        long start = System.nanoTime();
        Duration timeout = new Duration(30, SECONDS);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                assertion.run();
                return;
            }
            catch (Exception | AssertionError e) {
                if (Duration.nanosSince(start).compareTo(timeout) > 0) {
                    throw e;
                }
            }
            try {
                //noinspection BusyWait
                Thread.sleep(50);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }
}
