package io.airlift.jaxrs.testing;

import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.Request;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.http.client.testing.TestingHttpClient;
import org.testng.annotations.Test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import java.net.URI;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

@Test
public class TestJaxrsTestingHttpProcessor
{
    private static final TestingHttpClient HTTP_CLIENT =
            new TestingHttpClient(new JaxrsTestingHttpProcessor(URI.create("http://fake.invalid/"), new GetItResource()));

    @Test
    public void test()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(URI.create("http://fake.invalid/get-it/get/xyz"))
                .build();

        StringResponse response = HTTP_CLIENT.execute(request, createStringResponseHandler());

        assertEquals(response.getStatusCode(), HttpStatus.OK.code());
        assertEquals(response.getBody(), "Got xyz");
    }

    @Test
    public void testException()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(URI.create("http://fake.invalid/get-it/fail/testException"))
                .build();

        try {
            HTTP_CLIENT.execute(request, createStringResponseHandler());
            fail("expected exception");
        }
        catch (TestingException e) {
            assertEquals(e.getMessage(), "testException");
        }
    }

    @Path("get-it")
    public static class GetItResource
    {
        @Path("get/{id}")
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String getId(@PathParam("id") String id)
        {
            return format("Got %s", id);
        }

        @Path("fail/{message}")
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String fail(@PathParam("message") String errorMessage)
        {
            throw new TestingException(errorMessage);
        }
    }

    private static class TestingException
            extends RuntimeException
    {
        public TestingException(String message)
        {
            super(message);
        }
    }
}
