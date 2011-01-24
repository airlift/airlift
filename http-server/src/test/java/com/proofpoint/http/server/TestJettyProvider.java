package com.proofpoint.http.server;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.ning.http.util.Base64;
import org.eclipse.jetty.server.Server;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutionException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestJettyProvider
{
    private Server server;
    private File tempDir;

    @BeforeMethod
    public void setup()
            throws IOException
    {
        tempDir = Files.createTempDir().getCanonicalFile(); // getCanonicalFile needed to get around Issue 365 (http://code.google.com/p/guava-libraries/issues/detail?id=365)
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
            Files.deleteRecursively(tempDir);
        }
    }

    @Test
    public void testHttp()
            throws Exception
    {
        HttpServerConfig config = makeBaseConfig();

        createServer(config);
        server.start();

        AsyncHttpClient client = new AsyncHttpClient();
        Response response = client.prepareGet("http://localhost:" + config.getHttpPort() + "/")
                .execute()
                .get();

        assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
    }

    @Test
    public void testHttpIsDisabled()
            throws Exception
    {
        HttpServerConfig config = makeBaseConfig()
                .setHttpEnabled(false);

        createServer(config);
        server.start();

        AsyncHttpClient client = new AsyncHttpClient();
        try {
            Response response = client.prepareGet("http://localhost:" + config.getHttpPort() + "/")
                    .execute()
                    .get();

            if (response != null) { // TODO: this is a workaround for a bug in AHC (some race condition)
                fail("Expected connection refused, got response code: " + response.getStatusCode());
            }
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof ConnectException);
        }
    }

    public void testHttps()
    {
        // TODO
    }

    @Test
    public void testAuth()
            throws Exception
    {
        File file = File.createTempFile("auth", ".properties", tempDir);
        Files.write("user: password", file, Charsets.UTF_8);

        HttpServerConfig config = makeBaseConfig()
            .setUserAuthFile(file.getAbsolutePath());

        createServer(config);
        server.start();

        AsyncHttpClient client = new AsyncHttpClient();
        Response response = client.prepareGet("http://localhost:" + config.getHttpPort() + "/")
                .addHeader("Authorization", "Basic " + Base64.encode("user:password".getBytes()))
                .execute()
                .get();

        assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
        assertEquals(response.getResponseBody(), "user");
    }

    public void testJMX()
    {
        // TODO
    }

    public void testStats()
    {
        // TODO
    }

    public void testGzipRequest()
    {
        // TODO
    }

    public void testGzipResponse()
    {
        // TODO
    }

    public void testLogPathIsNotFile()
    {
        // TODO
    }

    public void testLogPathParentCannotbeCreated()
    {
        // TODO
    }

    private void createServer(HttpServerConfig config)
    {
        HashLoginServiceProvider loginServiceProvider = new HashLoginServiceProvider(config);
        JettyProvider provider = new JettyProvider(config, new DummyServlet());
        provider.setLoginService(loginServiceProvider.get());
        server = provider.get();
    }

    private HttpServerConfig makeBaseConfig()
            throws IOException
    {
        // TODO: replace with NetUtils.findUnusedPort()
        ServerSocket socket = new ServerSocket();
        try {
            socket.bind(new InetSocketAddress(0));
            final int port = socket.getLocalPort();

            return new HttpServerConfig()
                .setHttpPort(port)
                .setLogPath(new File(tempDir, "http-request.log").getAbsolutePath());
        }
        finally {
            socket.close();
        }
    }
}
