package io.airlift.http.client.testing;

import com.google.common.net.MediaType;
import io.airlift.http.client.AbstractHttpClientTest.CaptureExceptionResponseHandler;
import io.airlift.http.client.AbstractHttpClientTest.CapturedException;
import io.airlift.http.client.HttpClient.HttpResponseFuture;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.http.client.ResponseHandler;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.concurrent.ExecutionException;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.http.client.testing.TestingResponse.mockResponse;
import static io.airlift.testing.Assertions.assertInstanceOf;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

public class TestTestingHttpClient
{
    @Test
    public void testBlocking()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(URI.create("http://example.org"))
                .build();
        Response expectedResponse = mockResponse(HttpStatus.BAD_REQUEST, MediaType.PLAIN_TEXT_UTF_8, "");

        String result = new TestingHttpClient(
                input -> {
                    assertSame(input, request);
                    return expectedResponse;
                })
                .execute(request, new ResponseHandler<String, RuntimeException>()
                {
                    @Override
                    public String handleException(Request request, Exception exception)
                    {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String handle(Request request, Response response)
                    {
                        assertSame(response, expectedResponse);
                        return "expected response";
                    }
                });

        assertEquals(result, "expected response");
    }

    @Test
    public void testAsync()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(URI.create("http://example.org"))
                .build();
        Response expectedResponse = mockResponse(HttpStatus.BAD_REQUEST, MediaType.PLAIN_TEXT_UTF_8, "");

        HttpResponseFuture<String> future = new TestingHttpClient(
                input -> {
                    assertSame(input, request);
                    return expectedResponse;
                })
                .executeAsync(request, new ResponseHandler<String, RuntimeException>()
                {
                    @Override
                    public String handleException(Request request, Exception exception)
                    {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String handle(Request request, Response response)
                    {
                        assertSame(response, expectedResponse);
                        return "expected response";
                    }
                });

        assertEquals(future.get(), "expected response");
    }

    @Test
    public void testExceptionFromProcessor()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(URI.create("http://example.org"))
                .build();

        RuntimeException expectedException = new RuntimeException("test exception");

        HttpResponseFuture<String> future = new TestingHttpClient(input -> {
            throw expectedException;
        }).executeAsync(request, new CaptureExceptionResponseHandler());

        try {
            future.get();
            fail("expected exception");
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertInstanceOf(cause, CapturedException.class);
            assertEquals(cause.getCause(), expectedException);
        }
    }

    @Test
    public void testReplaceProcessor()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(URI.create("http://example.org"))
                .build();
        Response expectedResponse = mockResponse(HttpStatus.BAD_REQUEST, MediaType.PLAIN_TEXT_UTF_8, "");

        TestingHttpClient client = new TestingHttpClient(input -> {
            throw new UnsupportedOperationException();
        });

        client.setProcessor(input -> {
            assertSame(input, request);
            return expectedResponse;
        });

        HttpResponseFuture<String> future = client
                .executeAsync(request, new ResponseHandler<String, RuntimeException>()
                {
                    @Override
                    public String handleException(Request request, Exception exception)
                    {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String handle(Request request, Response response)
                    {
                        assertSame(response, expectedResponse);
                        return "expected response";
                    }
                });

        assertEquals(future.get(), "expected response");
    }

    @Test
    public void testDefaultConstructor()
    {
        TestingHttpClient client = new TestingHttpClient();
        try {
            client.execute(prepareGet().setUri(URI.create("http://example.org")).build(), createStatusResponseHandler());
            fail("Expected UnsupportedOperationException");
        }
        catch (UnsupportedOperationException ignored) {
        }
    }
}
