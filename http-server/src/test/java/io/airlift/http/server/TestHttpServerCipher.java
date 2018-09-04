/*
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
import io.airlift.node.NodeInfo;
import io.airlift.tracetoken.TraceTokenManager;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServlet;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static com.google.common.io.Resources.getResource;
import static org.testng.Assert.fail;

@Test(singleThreaded = true)
public class TestHttpServerCipher
{
    private static final String KEY_STORE_PATH = constructKeyStorePath();
    private static final String KEY_STORE_PASSWORD = "airlift";
    public static final String CIPHER_1 = "TLS_RSA_WITH_AES_128_CBC_SHA256";
    public static final String CIPHER_2 = "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256";
    public static final String CIPHER_3 = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256";

    private File tempDir;

    private static String constructKeyStorePath()
    {
        try {
            return new File(getResource("test.keystore").toURI()).getAbsolutePath();
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeMethod
    public void setup()
            throws IOException
    {
        tempDir = Files.createTempDir();
    }

    @AfterMethod(alwaysRun = true)
    public void teardown()
            throws Exception
    {
        deleteRecursively(tempDir.toPath(), ALLOW_INSECURE);
    }

    @Test
    public void testIncludeCipherEmpty()
            throws Exception
    {
        HttpServerConfig config = createHttpServerConfig()
                .setHttpsExcludedCipherSuites("")
                .setHttpsIncludedCipherSuites(" ,   ");
        NodeInfo nodeInfo = new NodeInfo("test");
        HttpServerInfo httpServerInfo = new HttpServerInfo(config, nodeInfo);
        HttpServer server = createServer(nodeInfo, httpServerInfo, config);
        try {
            server.start();
            URI httpsUri = httpServerInfo.getHttpsUri();

            HttpClient httpClient = createClientIncludeCiphers(CIPHER_1);
            httpClient.GET(httpsUri);

            httpClient = createClientIncludeCiphers(CIPHER_2);
            httpClient.GET(httpsUri);

            httpClient = createClientIncludeCiphers(CIPHER_3);
            httpClient.GET(httpsUri);
        }
        finally {
            server.stop();
        }
    }

    @Test
    public void testIncludedCipher()
            throws Exception
    {
        HttpServerConfig config = createHttpServerConfig()
                .setHttpsExcludedCipherSuites("")
                .setHttpsIncludedCipherSuites(CIPHER_1 + "," + CIPHER_2);
        NodeInfo nodeInfo = new NodeInfo("test");
        HttpServerInfo httpServerInfo = new HttpServerInfo(config, nodeInfo);
        HttpServer server = createServer(nodeInfo, httpServerInfo, config);
        try {
            server.start();
            URI httpsUri = httpServerInfo.getHttpsUri();

            // should succeed because only one of the two allowed certificate is excluded
            HttpClient httpClient = createClientIncludeCiphers(CIPHER_1);
            httpClient.GET(httpsUri);

            // should succeed because only one of the two allowed certificate is excluded
            httpClient = createClientIncludeCiphers(CIPHER_2);
            httpClient.GET(httpsUri);

            httpClient = createClientIncludeCiphers(CIPHER_3);
            try {
                httpClient.GET(httpsUri);
                fail("SSL handshake should fail because client included only ciphers the server didn't include");
            }
            catch (ExecutionException e) {
                // expected
            }
        }
        finally {
            server.stop();
        }
    }

    @Test
    public void testExcludedCipher()
            throws Exception
    {
        HttpServerConfig config = createHttpServerConfig()
                .setHttpsExcludedCipherSuites(CIPHER_1 + "," + CIPHER_2);
        NodeInfo nodeInfo = new NodeInfo("test");
        HttpServerInfo httpServerInfo = new HttpServerInfo(config, nodeInfo);
        HttpServer server = createServer(nodeInfo, httpServerInfo, config);

        try {
            server.start();
            URI httpsUri = httpServerInfo.getHttpsUri();

            // should succeed because all ciphers accepted
            HttpClient httpClient = createClientIncludeCiphers();
            httpClient.GET(httpsUri);

            httpClient = createClientIncludeCiphers(CIPHER_1, CIPHER_2);
            try {
                httpClient.GET(httpsUri);
                fail("SSL handshake should fail because client included only ciphers the server excluded");
            }
            catch (ExecutionException e) {
                // expected
            }
        }
        finally {
            server.stop();
        }
    }

    private HttpServerConfig createHttpServerConfig()
    {
        return new HttpServerConfig()
                .setHttpEnabled(false)
                .setHttpsEnabled(true)
                .setHttpsPort(0)
                .setKeystorePath(KEY_STORE_PATH)
                .setKeystorePassword(KEY_STORE_PASSWORD)
                .setLogPath(new File(tempDir, "http-request.log").getAbsolutePath());
    }

    private static HttpClient createClientIncludeCiphers(String... includedCipherSuites)
            throws Exception
    {
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setIncludeCipherSuites(includedCipherSuites);
        // Since Jetty 9.4.12 the list of excluded cipher suites includes "^TLS_RSA_.*$" by default.
        // We reset that list here to enable use of those cipher suites.
        sslContextFactory.setExcludeCipherSuites();
        sslContextFactory.setKeyStorePath(KEY_STORE_PATH);
        sslContextFactory.setKeyStorePassword(KEY_STORE_PASSWORD);
        HttpClient httpClient = new HttpClient(sslContextFactory);
        httpClient.start();
        return httpClient;
    }

    private static HttpServer createServer(NodeInfo nodeInfo, HttpServerInfo httpServerInfo, HttpServerConfig config)
    {
        return createServer(new DummyServlet(), nodeInfo, httpServerInfo, config);
    }

    private static HttpServer createServer(HttpServlet servlet, NodeInfo nodeInfo, HttpServerInfo httpServerInfo, HttpServerConfig config)
    {
        HashLoginServiceProvider loginServiceProvider = new HashLoginServiceProvider(config);
        HttpServerProvider serverProvider = new HttpServerProvider(
                httpServerInfo,
                nodeInfo,
                config,
                servlet,
                ImmutableSet.of(new DummyFilter()),
                ImmutableSet.of(),
                ImmutableSet.of(),
                new RequestStats(),
                new NullEventClient());
        serverProvider.setTheAdminServlet(new DummyServlet());
        serverProvider.setLoginService(loginServiceProvider.get());
        serverProvider.setTokenManager(new TraceTokenManager());
        return serverProvider.get();
    }
}
