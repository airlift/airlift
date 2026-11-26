package io.airlift.jaxrs.testing;

import io.airlift.http.client.AsyncBodyGenerator;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.Request;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.http.client.testing.TestingHttpClient;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.airlift.http.client.AsyncBodyGenerator.asyncBodyGenerator;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StreamingBodyGenerator.streamingBodyGenerator;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

public class TestJaxrsTestingHttpProcessor
{
    private static final TestingHttpClient HTTP_CLIENT =
            new TestingHttpClient(new JaxrsTestingHttpProcessor(URI.create("http://fake.invalid/"), new GetItResource()));
    // set by the only test that uses the resource that reads it
    private static final AtomicReference<AsyncBodyGenerator> BODY_FAILED_BY_RESOURCE = new AtomicReference<>();

    @Test
    public void test()
    {
        Request request = prepareGet()
                .setUri(URI.create("http://fake.invalid/get-it/get/xyz"))
                .setHeader(HeaderName.of("X-Test"), "abc")
                .build();

        StringResponse response = HTTP_CLIENT.execute(request, createStringResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.code());
        assertThat(response.getBody()).isEqualTo("Got xyz");
        assertThat(response.getHeader(HeaderName.of("X-Test-Out"))).hasValue("Got abc");
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
        assertThat(response.getHeader(CONTENT_TYPE)).hasValue("application/vnd.sun.wadl+xml");
        assertThat(response.getBody()).startsWith("<?xml ").contains("<application ");
    }

    @Test
    public void testStreamingBodyWithoutContentTypeHeader()
    {
        // the content type of the body is used, because the request has no header for it
        Request request = preparePost()
                .setUri(URI.create("http://fake.invalid/get-it/echo"))
                .setBodyGenerator(streamingBodyGenerator(PLAIN_TEXT_UTF_8, new ByteArrayInputStream("Hello world".getBytes(UTF_8))))
                .build();

        StringResponse response = HTTP_CLIENT.execute(request, createStringResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.code());
        assertThat(response.getBody()).isEqualTo("Hello world");
    }

    @Test
    public void testAsyncBody()
    {
        // the content type of the body is used, because the request has no header for it
        AsyncBodyGenerator body = asyncBodyGenerator(PLAIN_TEXT_UTF_8);
        body.write(ByteBuffer.wrap("Hello ".getBytes(UTF_8)));
        body.write(ByteBuffer.wrap("world".getBytes(UTF_8)));
        body.close();
        Request request = preparePost()
                .setUri(URI.create("http://fake.invalid/get-it/echo"))
                .setBodyGenerator(body)
                .build();

        StringResponse response = HTTP_CLIENT.execute(request, createStringResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.code());
        assertThat(response.getBody()).isEqualTo("Hello world");
    }

    @Test
    public void testOpenAsyncBodyIsRejected()
    {
        AsyncBodyGenerator body = asyncBodyGenerator(PLAIN_TEXT_UTF_8);
        body.write(ByteBuffer.wrap("Hello".getBytes(UTF_8)));
        Request request = preparePost()
                .setUri(URI.create("http://fake.invalid/get-it/echo"))
                .setBodyGenerator(body)
                .build();

        assertThatThrownBy(() -> HTTP_CLIENT.execute(request, createStringResponseHandler()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AsyncBodyGenerator must be closed before the request is executed");

        // the request has ended, so the body does not accept more content
        assertThatThrownBy(() -> body.write(ByteBuffer.wrap("late".getBytes(UTF_8))).get())
                .hasRootCauseMessage("AsyncBodyGenerator must be closed before the request is executed");
    }

    @Test
    public void testAsyncBodyFailsWhileResourceRuns()
    {
        AsyncBodyGenerator body = asyncBodyGenerator(PLAIN_TEXT_UTF_8);
        body.write(ByteBuffer.wrap("Hello".getBytes(UTF_8)));
        body.close();
        Request request = preparePost()
                .setUri(URI.create("http://fake.invalid/get-it/fail-body"))
                .setBodyGenerator(body)
                .build();

        // the resource got the complete body, but the request failed before the response
        BODY_FAILED_BY_RESOURCE.set(body);
        assertThatThrownBy(() -> HTTP_CLIENT.execute(request, createStringResponseHandler()))
                .isInstanceOf(TestingException.class)
                .hasMessage("failed while Hello was processed");
    }

    @Path("get-it")
    public static class GetItResource
    {
        @Path("fail-body")
        @POST
        @Consumes(MediaType.TEXT_PLAIN)
        @Produces(MediaType.TEXT_PLAIN)
        public String failBody(String body)
        {
            BODY_FAILED_BY_RESOURCE.get().fail(new TestingException("failed while " + body + " was processed"));
            return body;
        }

        @Path("echo")
        @POST
        @Consumes(MediaType.TEXT_PLAIN)
        @Produces(MediaType.TEXT_PLAIN)
        public String echo(String body)
        {
            return body;
        }

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
