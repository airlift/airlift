package com.proofpoint.jersey;

import com.google.common.io.Files;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.RequestType;
import com.ning.http.client.Response;
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
import java.util.concurrent.ExecutionException;

import static com.ning.http.client.RequestType.*;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import javax.ws.rs.core.Response.Status;

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
        tempDir = Files.createTempDir().getCanonicalFile(); // getCanonicalFile needed to get around Issue 365 (http://code.google.com/p/guava-libraries/issues/detail?id=365)
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
        client.prepareRequest(buildRequestWithQueryParam(POST, DELETE))
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
        client.prepareRequest(buildRequestWithQueryParam(POST, PUT))
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
        client.prepareRequest(buildRequestWithQueryParam(POST, POST))
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
        client.prepareRequest(buildRequestWithHeader(POST, DELETE))
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
        client.prepareRequest(buildRequestWithHeader(POST, PUT))
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
        client.prepareRequest(buildRequestWithHeader(POST, POST))
                .execute()
                .get();

        assertTrue(resource.postCalled(), "POST");
        assertFalse(resource.deleteCalled(), "DELETE");
        assertFalse(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }


    private void assertNonOverridableMethod(Request request)
            throws IOException, ExecutionException, InterruptedException
    {
        Response response = client.prepareRequest(request)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode());
        assertFalse(resource.postCalled(), "POST");
        assertFalse(resource.deleteCalled(), "DELETE");
        assertFalse(resource.putCalled(), "PUT");
        assertFalse(resource.getCalled(), "GET");
    }

    private Request buildRequestWithHeader(RequestType type, RequestType override)
    {
        return new RequestBuilder(type)
                .setUrl(format("http://localhost:%d/", config.getHttpPort()))
                .addHeader("X-HTTP-Method-Override", override.name())
                .build();
    }

    private Request buildRequestWithQueryParam(RequestType type, RequestType override)
    {
        return new RequestBuilder(type)
                .setUrl(format("http://localhost:%d/?_method=%s", config.getHttpPort(), override.name()))
                .build();
    }

    @Test
    public void testNonOverridableMethodsWithHeader()
            throws IOException, ExecutionException, InterruptedException
    {
        assertNonOverridableMethod(buildRequestWithHeader(GET, POST));
        assertNonOverridableMethod(buildRequestWithHeader(GET, DELETE));
        assertNonOverridableMethod(buildRequestWithHeader(GET, PUT));

        assertNonOverridableMethod(buildRequestWithHeader(DELETE, POST));
        assertNonOverridableMethod(buildRequestWithHeader(DELETE, GET));
        assertNonOverridableMethod(buildRequestWithHeader(DELETE, PUT));

        assertNonOverridableMethod(buildRequestWithHeader(PUT, POST));
        assertNonOverridableMethod(buildRequestWithHeader(PUT, DELETE));
        assertNonOverridableMethod(buildRequestWithHeader(PUT, GET));
    }

    @Test
    public void testNonOverridableMethodsWithQueryParam()
            throws IOException, ExecutionException, InterruptedException
    {
        assertNonOverridableMethod(buildRequestWithQueryParam(GET, POST));
        assertNonOverridableMethod(buildRequestWithQueryParam(GET, DELETE));
        assertNonOverridableMethod(buildRequestWithQueryParam(GET, PUT));

        assertNonOverridableMethod(buildRequestWithQueryParam(DELETE, POST));
        assertNonOverridableMethod(buildRequestWithQueryParam(DELETE, GET));
        assertNonOverridableMethod(buildRequestWithQueryParam(DELETE, PUT));

        assertNonOverridableMethod(buildRequestWithQueryParam(PUT, POST));
        assertNonOverridableMethod(buildRequestWithQueryParam(PUT, DELETE));
        assertNonOverridableMethod(buildRequestWithQueryParam(PUT, GET));
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
        public boolean get()
        {
            get = true;
            return true;
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
