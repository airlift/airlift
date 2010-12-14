package com.proofpoint.jersey;

import com.google.common.io.Files;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.ning.http.client.AsyncHttpClient;
import com.proofpoint.jetty.JettyConfig;
import com.proofpoint.jetty.JettyProvider;
import org.eclipse.jetty.server.Server;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import static java.lang.String.format;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestOverrideMethodFilterInJetty
{
    private Server server;
    private File tempDir;
    private JettyConfig config;
    private TestResource resource;
    private AsyncHttpClient client;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        tempDir = Files.createTempDir()
                .getCanonicalFile(); // getCanonicalFile needed to get around Issue 365 (http://code.google.com/p/guava-libraries/issues/detail?id=365)
        config = makeJettyConfig(tempDir);

        resource = new TestResource();
        server = createServer(config, resource);

        client = new AsyncHttpClient();
        
        server.start();
    }

    @AfterMethod
    public void teardown()
            throws Exception
    {
        try {
            if (server != null) {
                server.stop();
            }

            if (client != null) {
                client.close();
            }
        }
        finally {
            Files.deleteRecursively(tempDir);
        }
    }

    @Test
    public void testDeleteViaQueryParam()
            throws Exception
    {
        client.preparePost(format("http://localhost:%d/?_method=DELETE", config.getHttpPort()))
                .execute()
                .get();

        assertFalse(resource.postCalled(), "POST");
        assertTrue(resource.deleteCalled(), "DELETE");
        assertFalse(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }

    @Test
    public void testPutViaQueryParam()
            throws Exception
    {
        client.preparePost(format("http://localhost:%d/?_method=PUT", config.getHttpPort()))
                .execute()
                .get();

        assertFalse(resource.postCalled(), "POST");
        assertFalse(resource.deleteCalled(), "DELETE");
        assertTrue(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }


    @Test
    public void testPostViaQueryParam()
            throws Exception
    {
        client.preparePost(format("http://localhost:%d/?_method=POST", config.getHttpPort()))
                .execute()
                .get();

        assertTrue(resource.postCalled(), "POST");
        assertFalse(resource.deleteCalled(), "DELETE");
        assertFalse(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }

    @Test
    public void testDeleteViaHeader()
            throws Exception
    {
        client.preparePost(format("http://localhost:%d/", config.getHttpPort()))
                .addHeader("X-HTTP-Method-Override", "DELETE")
                .execute()
                .get();

        assertFalse(resource.postCalled(), "POST");
        assertTrue(resource.deleteCalled(), "DELETE");
        assertFalse(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }

    @Test
    public void testPutViaHeader()
            throws Exception
    {
        client.preparePost(format("http://localhost:%d/", config.getHttpPort()))
                .addHeader("X-HTTP-Method-Override", "PUT")
                .execute()
                .get();

        assertFalse(resource.postCalled(), "POST");
        assertFalse(resource.deleteCalled(), "DELETE");
        assertTrue(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }


    @Test
    public void testPostViaHeader()
            throws Exception
    {
        client.preparePost(format("http://localhost:%d/?_method=POST", config.getHttpPort()))
                .addHeader("X-HTTP-Method-Override", "POST")
                .execute()
                .get();

        assertTrue(resource.postCalled(), "POST");
        assertFalse(resource.deleteCalled(), "DELETE");
        assertFalse(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }




    private JettyConfig makeJettyConfig(final File tempDir)
            throws IOException
    {
        // TODO: replace with NetUtils.findUnusedPort()
        ServerSocket socket = new ServerSocket();
        socket.bind(new InetSocketAddress(0));
        final int port = socket.getLocalPort();
        socket.close();

        return new JettyConfig()
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
    }

    @Path("/")
    public static class TestResource
    {
        private volatile boolean post;
        private volatile boolean put;
        private volatile boolean get;
        private volatile boolean delete;

        @POST
        public void post()
        {
            post = true;
        }

        @GET
        public void get()
        {
            get = true;
        }

        @DELETE
        public void delete()
        {
            delete = true;
        }

        @PUT
        public void put()
        {
            put = true;
        }

        public boolean postCalled()
        {
            return post;
        }

        public boolean putCalled()
        {
            return put;
        }

        public boolean getCalled()
        {
            return get;
        }

        public boolean deleteCalled()
        {
            return delete;
        }
    }
    
    private Server createServer(final JettyConfig config, final TestResource resource)
    {
        return Guice.createInjector(new JerseyModule(),
                                    new Module()
                                    {
                                        @Override
                                        public void configure(Binder binder)
                                        {
                                            binder.bind(Server.class).toProvider(JettyProvider.class);
                                            binder.bind(JettyConfig.class).toInstance(config);
                                            binder.bind(TestResource.class).toInstance(resource);
                                        }
                                    }).getInstance(Server.class);
    }
}
