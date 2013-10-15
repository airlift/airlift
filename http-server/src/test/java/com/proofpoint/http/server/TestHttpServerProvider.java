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
package com.proofpoint.http.server;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.common.net.InetAddresses;
import com.proofpoint.http.client.ApacheHttpClient;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.HttpClientConfig;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.http.server.HttpServerBinder.HttpResourceBinding;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.node.NodeConfig;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.testing.FileUtils;
import com.proofpoint.tracetoken.TraceTokenManager;
import com.proofpoint.units.Duration;
import org.apache.commons.codec.binary.Base64;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

import static com.google.common.io.Resources.getResource;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestHttpServerProvider
{
    private static final String JAVAX_NET_SSL_TRUST_STORE = "javax.net.ssl.trustStore";
    private String originalTrustStore;
    private HttpServer server;
    private File tempDir;
    private NodeInfo nodeInfo;
    private HttpServerConfig config;
    private HttpServerInfo httpServerInfo;

    @BeforeMethod
    public void setup()
            throws IOException
    {
        originalTrustStore = System.getProperty(JAVAX_NET_SSL_TRUST_STORE);
        System.setProperty(JAVAX_NET_SSL_TRUST_STORE, getResource("localhost.keystore").getPath());
        tempDir = Files.createTempDir().getCanonicalFile(); // getCanonicalFile needed to get around Issue 365 (http://code.google.com/p/guava-libraries/issues/detail?id=365)
        config = new HttpServerConfig()
                .setHttpPort(0)
                .setLogPath(new File(tempDir, "http-request.log").getAbsolutePath());
        nodeInfo = new NodeInfo("test-application", new NodeConfig()
                .setEnvironment("test")
                .setNodeInternalIp(InetAddresses.forString("127.0.0.1"))
                .setNodeBindIp(InetAddresses.forString("127.0.0.1"))
                .setNodeExternalAddress("localhost")
                .setNodeInternalHostname("localhost")
        );
        httpServerInfo = new HttpServerInfo(config, nodeInfo);
    }

    @AfterMethod
    public void teardown()
            throws Exception
    {
        if (originalTrustStore != null) {
            System.setProperty(JAVAX_NET_SSL_TRUST_STORE, originalTrustStore);
        }
        else {
            System.clearProperty(JAVAX_NET_SSL_TRUST_STORE);
        }

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

        HttpClient client = new ApacheHttpClient();
        StatusResponse response = client.execute(prepareGet().setUri(httpServerInfo.getHttpUri()).build(), createStatusResponseHandler());

        assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
    }

    @Test
    public void testHttps()
            throws Exception
    {
        config.setHttpEnabled(false)
                .setHttpsEnabled(true)
                .setHttpsPort(0)
                .setKeystorePath(getResource("localhost.keystore").toString())
                .setKeystorePassword("changeit");
        httpServerInfo = new HttpServerInfo(config, nodeInfo);

        createServer();
        server.start();

        HttpClient client = new ApacheHttpClient();
        StatusResponse response = client.execute(prepareGet().setUri(httpServerInfo.getHttpsUri()).build(), createStatusResponseHandler());

        assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
    }

    @Test
    public void testFilter()
            throws Exception
    {
        createServer();
        server.start();

        HttpClient client = new ApacheHttpClient();
        StatusResponse response = client.execute(prepareGet().setUri(httpServerInfo.getHttpUri().resolve("/filter")).build(), createStatusResponseHandler());

        assertEquals(response.getStatusCode(), HttpServletResponse.SC_PAYMENT_REQUIRED);
        assertEquals(response.getStatusMessage(), "filtered");
    }

    @Test
    public void testHttpIsDisabled()
            throws Exception
    {
        config.setHttpEnabled(false);

        createServer();
        server.start();

        HttpClient client = new ApacheHttpClient(new HttpClientConfig().setConnectTimeout(new Duration(2.0, TimeUnit.SECONDS)));
        try {
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
        Files.write("user: password", file, Charsets.UTF_8);

        config.setUserAuthFile(file.getAbsolutePath());

        createServer();
        server.start();

        HttpClient client = new ApacheHttpClient();
        StringResponse response = client.execute(
                prepareGet()
                        .setUri(httpServerInfo.getHttpUri())
                        .addHeader("Authorization", "Basic " + Base64.encodeBase64String("user:password".getBytes()).trim())
                        .build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
        assertEquals(response.getBody(), "user");
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "insufficient threads configured for HTTP connector")
    public void testInsufficientThreadsHttp()
            throws Exception
    {
        config.setMaxThreads(1);
        createAndStartServer();
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "insufficient threads configured for HTTPS connector")
    public void testInsufficientThreadsHttps()
            throws Exception
    {
        config.setHttpEnabled(false)
                .setHttpsEnabled(true)
                .setHttpsPort(0)
                .setKeystorePath(getResource("localhost.keystore").toString())
                .setKeystorePassword("changeit")
                .setMaxThreads(1);
        createAndStartServer();
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
        HashLoginServiceProvider loginServiceProvider = new HashLoginServiceProvider(config);
        HttpServerProvider serverProvider = new HttpServerProvider(httpServerInfo,
                nodeInfo,
                config,
                new DummyServlet(),
                ImmutableSet.<Filter>of(new DummyFilter()),
                ImmutableSet.<HttpResourceBinding>of(),
                ImmutableSet.<Filter>of(),
                new RequestStats(),
                new TestingHttpServer.DetailedRequestStats(),
                new QueryStringFilter()
        );
        serverProvider.setLoginService(loginServiceProvider.get());
        serverProvider.setTokenManager(new TraceTokenManager());
        server = serverProvider.get();
    }
}
