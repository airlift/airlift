package io.airlift.jaxrs;

import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.client.StringResponseHandler;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.testing.TestingHttpServer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.jaxrs.TestOverrideMethodFilterInHttpServer.createServer;
import static io.airlift.testing.Closeables.closeQuietly;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static org.testng.Assert.assertEquals;

@Test(singleThreaded = true)
public class TestMethodContext
{
    private HttpClient client;
    private ExecutorService executor;

    @BeforeClass
    public void setup()
    {
        client = new JettyHttpClient();
        executor = newCachedThreadPool();
    }

    @AfterClass(alwaysRun = true)
    public void teardown()
    {
        closeQuietly(client);
        executor.shutdownNow();
    }

    public void testContextThreading()
            throws Exception
    {
        TestingHttpServer server = createServer(new TestingResource());
        try {
            Request request = prepareGet()
                    .setUri(server.getBaseUrl().resolve("/test"))
                    .build();
            StringResponseHandler.StringResponse response = client.execute(request, createStringResponseHandler());
            assertEquals(response.getBody(), "path=/test");
            System.out.println(response.getBody());
        }
        finally {
            server.stop();
        }
    }

    @Path("/test")
    public class TestingResource
    {
        @GET
        public Response getData(@Context UriInfo uriInfo)
                throws ExecutionException, InterruptedException
        {
            String path = executor.submit((Callable<String>) uriInfo::getPath).get();

            return Response.ok("path=" + path, TEXT_PLAIN_TYPE).build();
        }
    }
}
