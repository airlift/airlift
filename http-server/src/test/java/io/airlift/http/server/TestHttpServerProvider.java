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

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import io.airlift.event.client.NullEventClient;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpClient.HttpResponseFuture;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.RuntimeIOException;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.HttpServerBinder.HttpResourceBinding;
import io.airlift.log.Logging;
import io.airlift.node.NodeInfo;
import io.airlift.testing.FileUtils;
import io.airlift.tracetoken.TraceTokenManager;
import io.airlift.units.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import javax.servlet.Filter;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.util.Base64;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.google.common.io.Resources.getResource;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.testing.Assertions.assertContains;
import static io.airlift.testing.Assertions.assertInstanceOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test(singleThreaded = true)
public class TestHttpServerProvider
{
    private HttpServer server;
    private File tempDir;
    private NodeInfo nodeInfo;
    private HttpServerConfig config;
    private HttpServerInfo httpServerInfo;

    @BeforeSuite
    public void setupSuite()
    {
        Logging.initialize();
    }

    @BeforeMethod
    public void setup()
            throws IOException
    {
        tempDir = Files.createTempDir().getCanonicalFile(); // getCanonicalFile needed to get around Issue 365 (http://code.google.com/p/guava-libraries/issues/detail?id=365)
        config = new HttpServerConfig()
                .setHttpPort(0)
                .setLogPath(new File(tempDir, "http-request.log").getAbsolutePath());
        nodeInfo = new NodeInfo("test");
        httpServerInfo = new HttpServerInfo(config, nodeInfo);
    }

    @AfterMethod(alwaysRun = true)
    public void teardown()
            throws Exception
    {
        try {
            if (server != null) {
                server.stop();
            }
        }
        finally {
            FileUtils.deleteRecursively(tempDir);
        }
    }

    @Test
    public void testHttp()
            throws Exception
    {
        createServer();
        server.start();

        try (JettyHttpClient httpClient = new JettyHttpClient(new HttpClientConfig().setHttp2Enabled(false))) {
            StatusResponse response = httpClient.execute(prepareGet().setUri(httpServerInfo.getHttpUri()).build(), createStatusResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            assertEquals(response.getHeader("X-Protocol"), "HTTP/1.1");
        }

        try (JettyHttpClient httpClient = new JettyHttpClient(new HttpClientConfig().setHttp2Enabled(true))) {
            StatusResponse response = httpClient.execute(prepareGet().setUri(httpServerInfo.getHttpUri()).build(), createStatusResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            assertEquals(response.getHeader("X-Protocol"), "HTTP/2.0");
        }
    }

    @Test
    public void testFilter()
            throws Exception
    {
        createServer();
        server.start();

        try (JettyHttpClient client = new JettyHttpClient()) {
            StatusResponse response = client.execute(prepareGet().setUri(httpServerInfo.getHttpUri().resolve("/filter")).build(), createStatusResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_PAYMENT_REQUIRED);
            assertEquals(response.getStatusMessage(), "filtered");
        }
    }

    @Test
    public void testHttpIsDisabled()
            throws Exception
    {
        config.setHttpEnabled(false);

        createServer();
        server.start();

        try (HttpClient client = new JettyHttpClient(new HttpClientConfig().setConnectTimeout(new Duration(2.0, TimeUnit.SECONDS)))) {
            StatusResponse response = client.execute(prepareGet().setUri(httpServerInfo.getHttpUri().resolve("/")).build(), createStatusResponseHandler());

            if (response != null) { // TODO: this is a workaround for a bug in AHC (some race condition)
                fail("Expected connection refused, got response code: " + response.getStatusCode());
            }
        }
        catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof ConnectException, e.getCause().getClass() + " instanceof ConnectException");
        }
    }

    @Test
    public void testAuth()
            throws Exception
    {
        File file = File.createTempFile("auth", ".properties", tempDir);
        Files.write("user: password", file, UTF_8);

        config.setUserAuthFile(file.getAbsolutePath());

        createServer();
        server.start();

        try (HttpClient client = new JettyHttpClient()) {
            StringResponse response = client.execute(
                    prepareGet()
                            .setUri(httpServerInfo.getHttpUri())
                            .addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString("user:password".getBytes()).trim())
                            .build(),
                    createStringResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            assertEquals(response.getBody(), "user");
        }
    }

    @Test
    public void testShowStackTraceEnabled()
            throws Exception
    {
        createServer(new ErrorServlet());
        server.start();

        try (HttpClient client = new JettyHttpClient()) {
            StringResponse response = client.execute(prepareGet().setUri(httpServerInfo.getHttpUri()).build(), createStringResponseHandler());
            assertEquals(response.getStatusCode(), 500);
            assertContains(response.getBody(), "ErrorServlet.java");
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
            assertEquals(response.getStatusCode(), 500);
            assertTrue(!response.getBody().contains("ErrorServlet.java"));
        }
    }

    @Test(timeOut = 60000)
    public void testStop()
            throws Exception
    {
        createAndStartServer();

        try (HttpClient client = new JettyHttpClient()) {
            URI uri = URI.create(httpServerInfo.getHttpUri().toASCIIString() + "/?sleep=50000");
            Request request = prepareGet().setUri(uri).build();
            HttpResponseFuture<?> future = client.executeAsync(request, createStatusResponseHandler());

            server.stop();

            try {
                future.get(1, TimeUnit.SECONDS);
                fail("expected exception");
            }
            catch (ExecutionException e) {
                assertInstanceOf(e.getCause(), RuntimeIOException.class);
            }
        }
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Insufficient threads: .*")
    public void testInsufficientThreadsHttp()
            throws Exception
    {
        config.setMaxThreads(1);
        createAndStartServer();
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Insufficient threads: .*")
    public void testInsufficientThreadsHttps()
            throws Exception
    {
        config.setHttpEnabled(false)
                .setHttpsEnabled(true)
                .setHttpsPort(0)
                .setKeystorePath(getResource("test.keystore").toString())
                .setKeystorePassword("airlift")
                .setMaxThreads(1);
        createAndStartServer();
    }

    @Test
    public void testHttpsDaysUntilCertificateExpiration()
            throws Exception
    {
        config.setHttpEnabled(false)
                .setHttpsEnabled(true)
                .setHttpsPort(0)
                .setKeystorePath(new File(getResource("test.keystore").toURI()).getAbsolutePath())
                .setKeystorePassword("airlift");
        createAndStartServer();
        Long daysUntilCertificateExpiration = server.getDaysUntilCertificateExpiration();
        assertNotNull(daysUntilCertificateExpiration);
        // todo this should be positive but the certificate is expired
        assertTrue(daysUntilCertificateExpiration < 0);
    }

    @Test
    public void testNoHttpsDaysUntilCertificateExpiration()
            throws Exception
    {
        config.setHttpEnabled(true)
                .setHttpsPort(0);
        createAndStartServer();
        assertNull(server.getDaysUntilCertificateExpiration());
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "insufficient threads configured for admin connector")
    public void testInsufficientThreadsAdmin()
            throws Exception
    {
        config.setAdminMaxThreads(1);
        createAndStartServer();
    }

    private void createAndStartServer()
            throws Exception
    {
        httpServerInfo = new HttpServerInfo(config, nodeInfo);
        createServer();
        server.start();
    }

    private void createServer()
    {
        createServer(new DummyServlet());
    }

    private void createServer(HttpServlet servlet)
    {
        HashLoginServiceProvider loginServiceProvider = new HashLoginServiceProvider(config);
        HttpServerProvider serverProvider = new HttpServerProvider(httpServerInfo,
                nodeInfo,
                config,
                servlet,
                ImmutableSet.<Filter>of(new DummyFilter()),
                ImmutableSet.<HttpResourceBinding>of(),
                ImmutableSet.<Filter>of(),
                new RequestStats(),
                new NullEventClient());
        serverProvider.setTheAdminServlet(new DummyServlet());
        serverProvider.setLoginService(loginServiceProvider.get());
        serverProvider.setTokenManager(new TraceTokenManager());
        server = serverProvider.get();
    }
}
