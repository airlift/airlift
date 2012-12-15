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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import io.airlift.event.client.NullEventClient;
import io.airlift.http.client.ApacheHttpClient;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.node.NodeInfo;
import io.airlift.testing.FileUtils;
import io.airlift.tracetoken.TraceTokenManager;
import io.airlift.units.Duration;
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
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestHttpServerProvider
{
    private HttpServer server;
    private File tempDir;
    private NodeInfo nodeInfo;
    private HttpServerConfig config;
    private HttpServerInfo httpServerInfo;

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

    @AfterMethod
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

        HttpClient client = new ApacheHttpClient();
        StatusResponse response = client.execute(prepareGet().setUri(httpServerInfo.getHttpUri()).build(), createStatusResponseHandler());

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
                .setKeystorePath(getResource("test.keystore").toString())
                .setKeystorePassword("airlift")
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
                ImmutableSet.<Filter>of(),
                new RequestStats(),
                new NullEventClient());
        serverProvider.setTheAdminServlet(new DummyServlet());
        serverProvider.setLoginService(loginServiceProvider.get());
        serverProvider.setTokenManager(new TraceTokenManager());
        server = serverProvider.get();
    }
}
