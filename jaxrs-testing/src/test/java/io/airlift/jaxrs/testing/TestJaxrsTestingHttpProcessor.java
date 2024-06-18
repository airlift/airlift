package io.airlift.jaxrs.testing;

import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.Request;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.http.client.testing.TestingHttpClient;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.testng.annotations.Test;

import java.net.URI;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@Test
public class TestJaxrsTestingHttpProcessor
{
    private static final TestingHttpClient HTTP_CLIENT =
            new TestingHttpClient(new JaxrsTestingHttpProcessor(URI.create("http://fake.invalid/"), new GetItResource()));

    @Test
    public void test()
    {
        Request request = prepareGet()
                .setUri(URI.create("http://fake.invalid/get-it/get/xyz"))
                .setHeader("X-Test", "abc")
                .build();

        StringResponse response = HTTP_CLIENT.execute(request, createStringResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.code());
        assertThat(response.getBody()).isEqualTo("Got xyz");
        assertThat(response.getHeader("X-Test-Out")).isEqualTo("Got abc");
    }

    @Test
    public void testException()
    {
        Request request = prepareGet()
                .setUri(URI.create("http://fake.invalid/get-it/fail/testException"))
                .build();

        try {
            HTTP_CLIENT.execute(request, createStringResponseHandler());
            fail("expected exception");
        }
        catch (TestingException e) {
            assertThat(e.getMessage()).isEqualTo("testException");
        }
    }

    @Test
    public void testUndefinedResource()
    {
        Request request = prepareGet()
                .setUri(URI.create("http://fake.invalid/unknown"))
                .build();

        StringResponse response = HTTP_CLIENT.execute(request, createStringResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    public void testOptions()
    {
        Request request = new Request.Builder()
                .setMethod("OPTIONS")
                .setUri(URI.create("http://fake.invalid/get-it/get/xyz"))
                .build();

        StringResponse response = HTTP_CLIENT.execute(request, createStringResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(HTTP_OK);
        assertThat(response.getHeader(CONTENT_TYPE)).isEqualTo("application/vnd.sun.wadl+xml");
        assertThat(response.getBody()).startsWith("<?xml ").contains("<application ");
    }

    @Path("get-it")
    public static class GetItResource
    {
        @Path("get/{id}")
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public Response getId(
                @HeaderParam("X-Test") String test,
                @PathParam("id") String id)
        {
            return Response.ok("Got " + id)
                    .header("X-Test-Out", "Got " + test)
                    .build();
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
