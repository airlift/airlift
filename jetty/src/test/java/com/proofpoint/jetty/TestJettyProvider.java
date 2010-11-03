package com.proofpoint.jetty;

import com.google.common.io.Files;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.servlet.ServletModule;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.ning.http.util.Base64;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.server.Server;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
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
        tempDir = Files.createTempDir()
                .getCanonicalFile(); // getCanonicalFile needed to get around Issue 365 (http://code.google.com/p/guava-libraries/issues/detail?id=365)
    }
    
    @AfterMethod
    public void teardown()
            throws Exception
    {
        if (server != null) {
            server.stop();
        }

        Files.deleteRecursively(tempDir);
    }

    @Test
    public void testHttp()
            throws Exception
    {
        // TODO: replace with NetUtils.findUnusedPort()
        ServerSocket socket = new ServerSocket();
        socket.bind(new InetSocketAddress(0));
        final int port = socket.getLocalPort();
        socket.close();

        final JettyConfig config = new JettyConfig()
        {
            @Override
            public int getHttpPort()
            {
                return port;
            }

            @Override
            public String getLogPath()
            {
                return new File(tempDir, "jetty.log").getAbsolutePath();
            }
        };

        createServer(config);
        server.start();

        AsyncHttpClient client = new AsyncHttpClient();
        Response response = client.prepareGet("http://localhost:" + port + "/")
                .execute()
                .get();

        assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
    }

    @Test
    public void testHttpIsDisabled()
            throws Exception
    {
        // TODO: replace with NetUtils.findUnusedPort()
        ServerSocket socket = new ServerSocket();
        socket.bind(new InetSocketAddress(0));
        final int port = socket.getLocalPort();
        socket.close();

        final JettyConfig config = new JettyConfig()
        {
            @Override
            public int getHttpPort()
            {
                return port;
            }

            @Override
            public String getLogPath()
            {
                return new File(tempDir, "jetty.log").getAbsolutePath();
            }

            @Override
            public boolean isHttpEnabled()
            {
                return false;
            }
        };

        createServer(config);
        server.start();

        AsyncHttpClient client = new AsyncHttpClient();
        try {
            Response response = client.prepareGet("http://localhost:" + port + "/")
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
        // TODO: replace with NetUtils.findUnusedPort()
        ServerSocket socket = new ServerSocket();
        socket.bind(new InetSocketAddress(0));
        final int port = socket.getLocalPort();
        socket.close();

        final File file = File.createTempFile("auth", ".properties", tempDir);
        PrintStream out = new PrintStream(new FileOutputStream(file));
        try {
            out.print("user: password");
        }
        catch (Exception e) {
            out.close();
        }

        final JettyConfig config = new JettyConfig()
        {
            @Override
            public int getHttpPort()
            {
                return port;
            }

            @Override
            public String getLogPath()
            {
                return new File(tempDir, "jetty.log").getAbsolutePath();
            }

            @Override
            public String getUserAuthPath()
            {
                return file.getAbsolutePath();
            }
        };

        createServer(config);
        server.start();

        AsyncHttpClient client = new AsyncHttpClient();
        Response response = client.prepareGet("http://localhost:" + port + "/")
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

    private void createServer(final JettyConfig config)
    {
        server = Guice.createInjector(new ServletModule()
        {
            @Override
            protected void configureServlets()
            {
                serve("/*").with(DummyServlet.class);
            }
        }, new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                binder.bind(DummyServlet.class).in(Scopes.SINGLETON);
                binder.bind(Server.class).toProvider(JettyProvider.class);
                binder.bind(JettyConfig.class).toInstance(config);
                binder.bind(LoginService.class).toProvider(HashLoginServiceProvider.class);
            }
        }).getInstance(Server.class);
    }

    private static class DummyServlet
        extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException
        {
            resp.setStatus(HttpServletResponse.SC_OK);
            if (req.getUserPrincipal() != null) {
                resp.getOutputStream().write(req.getUserPrincipal().getName().getBytes());
            }
        }
    }
}
